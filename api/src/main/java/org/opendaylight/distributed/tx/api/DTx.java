package org.opendaylight.distributed.tx.api;

import com.google.common.util.concurrent.CheckedFuture;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

/**
 * Distributed transaction working with a set of nodes.
 *
 * Reusing MD-SAL transaction API, adding node specific data modification operations.
 */
public interface DTx extends WriteTransaction {
    /**
     * Delete piece of data for a specific node, that is encapsulated by this distributed transaction
     *
     * @param logicalDatastoreType ds type
     * @param instanceIdentifier IID for data
     * @param nodeId IID for node to invoke delete
     *
     * @throws DTxException.EditFailedException thrown when delete fails, but rollback was successful
     * @throws DTxException.RollbackFailedException  thrown when delete fails and rollback fails as well
     */
    @Deprecated
    void delete(LogicalDatastoreType logicalDatastoreType, InstanceIdentifier<?> instanceIdentifier,
        InstanceIdentifier<?> nodeId) throws DTxException.EditFailedException, DTxException.RollbackFailedException;
    ;

    /**
     *
     * Delete piece of data for a all nodes, that are encapsulated by this distributed transaction.
     *
     * // TODO do we want this here ?
     *
     * {@inheritDoc}
     */
    @Deprecated
    @Override void delete(LogicalDatastoreType logicalDatastoreType, InstanceIdentifier<?> instanceIdentifier)
        throws DTxException.EditFailedException;

    @Override <T extends DataObject> void merge(LogicalDatastoreType logicalDatastoreType,
        InstanceIdentifier<T> instanceIdentifier, T t)
        throws
        DTxException.EditFailedException,
        DTxException.RollbackFailedException;

    // TODO Document and add Rollback failed to declaration

    @Deprecated
    @Override <T extends DataObject> void merge(LogicalDatastoreType logicalDatastoreType,
        InstanceIdentifier<T> instanceIdentifier, T t, boolean b) throws DTxException.EditFailedException;

    @Deprecated
    @Override <T extends DataObject> void put(LogicalDatastoreType logicalDatastoreType,
        InstanceIdentifier<T> instanceIdentifier, T t) throws DTxException.EditFailedException;

    @Deprecated
    @Override <T extends DataObject> void put(LogicalDatastoreType logicalDatastoreType,
        InstanceIdentifier<T> instanceIdentifier, T t, boolean b) throws DTxException.EditFailedException;

    @Deprecated
    <T extends DataObject> void merge(LogicalDatastoreType logicalDatastoreType,
        InstanceIdentifier<T> instanceIdentifier, T t, InstanceIdentifier<?> nodeId) throws
        DTxException.EditFailedException;

    @Deprecated
    <T extends DataObject> void merge(LogicalDatastoreType logicalDatastoreType,
        InstanceIdentifier<T> instanceIdentifier, T t, boolean b, InstanceIdentifier<?> nodeId) throws
        DTxException.EditFailedException;

    @Deprecated
    <T extends DataObject> void put(LogicalDatastoreType logicalDatastoreType,
        InstanceIdentifier<T> instanceIdentifier, T t, InstanceIdentifier<?> nodeId) throws
        DTxException.EditFailedException;

    @Deprecated
    <T extends DataObject> void put(LogicalDatastoreType logicalDatastoreType,
        InstanceIdentifier<T> instanceIdentifier, T t, boolean b, InstanceIdentifier<?> nodeId) throws
        DTxException.EditFailedException;

    @Override CheckedFuture<Void, TransactionCommitFailedException> submit()
        throws DTxException.SubmitFailedException,
        DTxException.RollbackFailedException;

    @Override boolean cancel()
        throws DTxException.RollbackFailedException;

    <T extends DataObject> CheckedFuture<Void, DTxException> mergeAndRollbackOnFailure(
            final LogicalDatastoreType logicalDatastoreType,
            final InstanceIdentifier<T> instanceIdentifier, final T t, final InstanceIdentifier<?> nodeId);

    <T extends DataObject> CheckedFuture<Void, DTxException> putAndRollbackOnFailure(
            final LogicalDatastoreType logicalDatastoreType,
            final InstanceIdentifier<T> instanceIdentifier, final T t, final InstanceIdentifier<?> nodeId);

    CheckedFuture<Void, DTxException> deleteAndRollbackOnFailure(
            final LogicalDatastoreType logicalDatastoreType,
            final InstanceIdentifier<?> instanceIdentifier,
            InstanceIdentifier<?> nodeId);

    CheckedFuture<Void, DTxException.RollbackFailedException> rollback();

    /* APIs for mixed tx providers start from here. */
    <T extends DataObject> CheckedFuture<Void, DTxException> mergeAndRollbackOnFailure(
            final DTXLogicalTXProviderType logicalTXProviderType,
            final LogicalDatastoreType logicalDatastoreType,
            final InstanceIdentifier<T> instanceIdentifier, final T t, final InstanceIdentifier<?> nodeId);

    <T extends DataObject> CheckedFuture<Void, DTxException> putAndRollbackOnFailure(
            final DTXLogicalTXProviderType logicalTXProviderType,
            final LogicalDatastoreType logicalDatastoreType,
            final InstanceIdentifier<T> instanceIdentifier, final T t, final InstanceIdentifier<?> nodeId);

    CheckedFuture<Void, DTxException> deleteAndRollbackOnFailure(
            final DTXLogicalTXProviderType logicalTXProviderType,
            final LogicalDatastoreType logicalDatastoreType,
            final InstanceIdentifier<?> instanceIdentifier,
            InstanceIdentifier<?> nodeId);
}
