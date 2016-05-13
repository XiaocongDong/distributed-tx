package org.opendaylight.distributed.tx.spi;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.CheckedFuture;
import org.opendaylight.controller.md.sal.binding.api.ReadWriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException;
import org.opendaylight.distributed.tx.api.DTxException;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

/**
 * Created by cisco on 11/24/15.
 */
public interface  DTXReadWriteTransaction extends  ReadWriteTransaction{

    <T extends DataObject> CheckedFuture<Void, DTxException> asyncPut(final LogicalDatastoreType logicalDatastoreType,
                                                                      final InstanceIdentifier<T> instanceIdentifier, final T t) ;
    <T extends DataObject> CheckedFuture<Void, DTxException>asyncMerge(final LogicalDatastoreType logicalDatastoreType,
                                                                                     final InstanceIdentifier<T> instanceIdentifier, final T t) ;
    CheckedFuture<Void, DTxException> asyncDelete(final LogicalDatastoreType logicalDatastoreType,
                                                                final InstanceIdentifier<?> instanceIdentifier) ;
    @Override void delete(final LogicalDatastoreType logicalDatastoreType,
                                 final InstanceIdentifier<?> instanceIdentifier) ;
    @Override <T extends DataObject> void merge(final LogicalDatastoreType logicalDatastoreType,
                                                       final InstanceIdentifier<T> instanceIdentifier, final T t, final boolean b) ;


    @Override <T extends DataObject> void put(final LogicalDatastoreType logicalDatastoreType,
                                                     final InstanceIdentifier<T> instanceIdentifier, final T t, final boolean ensureParents) ;

    @Override CheckedFuture<Void, TransactionCommitFailedException> submit() ;

    @Override public <T extends DataObject> CheckedFuture<Optional<T>, ReadFailedException> read(
            final LogicalDatastoreType logicalDatastoreType, final InstanceIdentifier<T> instanceIdentifier) ;

}
