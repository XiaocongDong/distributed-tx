package org.opendaylight.distributed.tx.impl.spi;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.CheckedFuture;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.opendaylight.controller.md.sal.binding.api.ReadWriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException;
import org.opendaylight.distributed.tx.api.DTXLogicalTXProviderType;
import org.opendaylight.distributed.tx.api.DTxException;
import org.opendaylight.distributed.tx.spi.CachedData;
import org.opendaylight.distributed.tx.spi.Rollback;
import org.opendaylight.distributed.tx.spi.TxCache;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.ModifyAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RollbackImpl implements Rollback {

    private static final Logger LOG = LoggerFactory.getLogger(RollbackImpl.class);

    @Override public CheckedFuture<Void, DTxException.RollbackFailedException> rollback(
            @Nonnull final Map<InstanceIdentifier<?>, ? extends TxCache> perNodeCachesByType,
            // @Nonnull final Map<DTXLogicalTXProviderType, Map<InstanceIdentifier<?>, ? extends TxCache>> perNodeCachesByType,
            @Nonnull final Map<InstanceIdentifier<?>, ? extends ReadWriteTransaction> perNodeRollbackTxs) {

        final List<ListenableFuture<Void>> perNodeRollbackSubmitFutures = Lists.newArrayListWithCapacity(perNodeRollbackTxs.size());

        LOG.info("FM: in rollback {} {}", perNodeCachesByType, perNodeRollbackTxs);
        // for (DTXLogicalTXProviderType type : perNodeCachesByType.keySet()) {
        //    Map<InstanceIdentifier<?>, ? extends TxCache> perNodeCaches = perNodeCachesByType.get(type);
            for (final Map.Entry<InstanceIdentifier<?>, ? extends TxCache> perNodeCacheEntry : perNodeCachesByType.entrySet()) {
                InstanceIdentifier<?> nodeId = perNodeCacheEntry.getKey();
                TxCache perNodeCache = perNodeCacheEntry.getValue();

                final ReadWriteTransaction perNodeRollbackTx = perNodeRollbackTxs.get(nodeId);
                for (CachedData cachedData : perNodeCache) {

                    // FIXME how to fix the incorrect capture types for IID and DataObject in the put call below ??
                    final InstanceIdentifier<DataObject> dataId = (InstanceIdentifier<DataObject>) cachedData.getId();

                    ModifyAction revertAction = getRevertAction(cachedData.getOperation(), cachedData.getData());

                    LOG.info("perNodeCache {}", revertAction);
                    switch (revertAction) {
                        case REPLACE: {
                            LOG.info("FM: rollback replace");
                            try {
                                perNodeRollbackTx.put(cachedData.getDsType(), dataId, cachedData.getData().get());
                                break;
                            } catch (Exception e) {
                                return Futures.immediateFailedCheckedFuture(new DTxException.RollbackFailedException(String
                                        .format("Unable to rollback change for node: %s, %s data: %s. Node in unknown state.",
                                                perNodeCacheEntry.getKey(), revertAction, dataId), e));
                            }
                        }
                        case DELETE: {
                            LOG.info("FM: rollback delete");
                            try {
                                // FIXME doing this on a netconf device with candidate might result in a failure
                                // (for the device where submit failed, the state was automatically rolledback)
                                // So we should read the data first if we really need to roll back
                                perNodeRollbackTx.delete(cachedData.getDsType(), dataId);
                                break;
                            } catch (Exception e) {
                                return Futures.immediateFailedCheckedFuture(new DTxException.RollbackFailedException(String
                                        .format("Unable to rollback change for node: %s, %s data: %s. Node in unknown state.",
                                                perNodeCacheEntry.getKey(), revertAction, dataId), e));
                            }
                        }
                        case NONE: {
                            LOG.info("FM: rollback none");
                            break;
                        }
                        default: {
                            LOG.info("FM: rollback default");
                            return Futures.immediateFailedCheckedFuture(new DTxException.RollbackFailedException(
                                    "Unable to handle rollback for node: " + perNodeCacheEntry.getKey() +
                                            ", revert action: " + revertAction + ". Unknown operation type"));
                        }
                    }
                }
                final CheckedFuture<Void, TransactionCommitFailedException> perNodeRollbackSumitFuture = perNodeRollbackTx.submit();
                perNodeRollbackSubmitFutures.add(perNodeRollbackSumitFuture);
                Futures.addCallback(perNodeRollbackSumitFuture, new LoggingRollbackCallback(perNodeCacheEntry.getKey()));
            }
        //}

        return aggregateRollbackFutures(perNodeRollbackSubmitFutures);
    }

    private static CheckedFuture<Void, DTxException.RollbackFailedException> aggregateRollbackFutures(
        final List<ListenableFuture<Void>> perNodeRollbackSubmitFutures) {
        final ListenableFuture<Void> aggregatedRollbackSubmitFuture = Futures
            .transform(Futures.allAsList(perNodeRollbackSubmitFutures), new Function<List<Void>, Void>() {
                @Nullable @Override public Void apply(@Nullable final List<Void> input) {
                    return null;
                }
            });

        return Futures.makeChecked(aggregatedRollbackSubmitFuture, new Function<Exception, DTxException.RollbackFailedException>() {
            @Nullable @Override public DTxException.RollbackFailedException apply(final Exception input) {
                return new DTxException.RollbackFailedException("Rollback submit failed", input);
            }
        });
    }

    private static ModifyAction getRevertAction(final ModifyAction operation, final Optional<DataObject> data) {
        switch (operation) {
        case MERGE: {
            return data.isPresent() ? ModifyAction.REPLACE : ModifyAction.DELETE;
        }
        case REPLACE: {
            return data.isPresent() ? ModifyAction.REPLACE : ModifyAction.DELETE;
        }
        case DELETE:
            return data.isPresent() ? ModifyAction.REPLACE : ModifyAction.NONE;
        }

        throw new IllegalStateException("Unexpected operation: " + operation);
    }

    private static final class LoggingRollbackCallback implements FutureCallback<Void> {

        private final InstanceIdentifier<?> perNodeCacheEntry;

        public LoggingRollbackCallback(final InstanceIdentifier<?> perNodeCacheEntry) {
            this.perNodeCacheEntry = perNodeCacheEntry;
        }

        @Override public void onSuccess(@Nullable final Void result) {
            LOG.debug("FM: Node: {} rolled back successfully", perNodeCacheEntry);
        }

        @Override public void onFailure(final Throwable t) {
            LOG.debug("FM: Unable to rollback Node: {}. Rollback FAILED", perNodeCacheEntry, t);
        }
    }
}
