package org.opendaylight.distributed.tx.impl;

import com.google.common.base.Function;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.CheckedFuture;
import org.junit.Assert;
import org.junit.Test;
import org.opendaylight.controller.md.sal.binding.api.ReadWriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.distributed.tx.api.DTxException;
import org.opendaylight.distributed.tx.impl.spi.CachingReadWriteTx;
import org.opendaylight.distributed.tx.impl.spi.RollbackImpl;
import org.opendaylight.yangtools.yang.binding.DataContainer;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import javax.annotation.Nullable;
import java.util.Map;
import java.util.Set;
import static org.junit.Assert.fail;

public class RollbackImplTest {
    InstanceIdentifier<TestData1> identifier1 = InstanceIdentifier.create(TestData1.class); // data identifier
    InstanceIdentifier<TestData2> identifier2 = InstanceIdentifier.create(TestData2.class);
    InstanceIdentifier<TestNode1> node1 = InstanceIdentifier.create(TestNode1.class);  //nodeId identifier
    InstanceIdentifier<TestNode2> node2 = InstanceIdentifier.create(TestNode2.class);

    private class TestData1 implements DataObject {
        @Override
        public Class<? extends DataContainer> getImplementedInterface() {
            return null;
        }
    }

    private class TestData2 implements DataObject{
        @Override
        public Class<? extends DataContainer> getImplementedInterface() {
            return null;
        }
    }

    private class TestNode1 implements DataObject {
        @Override
        public Class<? extends DataContainer> getImplementedInterface() {
            return null;
        }
    }

    private class TestNode2 implements DataObject{
        @Override
        public Class<? extends DataContainer> getImplementedInterface() {
            return null;
        }
    }

    /**
     *put data in node1 and node2
     *invoke rollback
     *rollback succeed
     *no data in all the nodes
     */
    @Test
    public void testRollBack() {
        DTXTestTransaction testTransaction1 = new DTXTestTransaction();
        DTXTestTransaction testTransaction2 = new DTXTestTransaction();

        final CachingReadWriteTx cachingReadWriteTx1 = new CachingReadWriteTx(testTransaction1); //nodeId1 caching transaction
        final CachingReadWriteTx cachingReadWriteTx2 = new CachingReadWriteTx(testTransaction2); //nodeId2 caching transaction

        CheckedFuture<Void, DTxException> f1 = cachingReadWriteTx1.asyncPut(LogicalDatastoreType.OPERATIONAL, identifier1, new TestData1());
        CheckedFuture<Void, DTxException> f2 = cachingReadWriteTx1.asyncPut(LogicalDatastoreType.OPERATIONAL, identifier2, new TestData2());
        CheckedFuture<Void, DTxException> f3 = cachingReadWriteTx2.asyncPut(LogicalDatastoreType.OPERATIONAL, identifier1, new TestData1());
        CheckedFuture<Void, DTxException> f4 = cachingReadWriteTx2.asyncPut(LogicalDatastoreType.OPERATIONAL, identifier2, new TestData2());

        try
        {
            f1.checkedGet();
            f2.checkedGet();
            f3.checkedGet();
            f4.checkedGet();

            //check if all the data has been put into the transactions
            Assert.assertEquals(1,testTransaction1.getTxDataSize(identifier1));
            Assert.assertEquals(1,testTransaction1.getTxDataSize(identifier2));
            Assert.assertEquals(1,testTransaction2.getTxDataSize(identifier1));
            Assert.assertEquals(1,testTransaction2.getTxDataSize(identifier2));
        }catch (Exception e)
        {
            fail("get the unexpected exception from the asyncPut");
        }

        Set<InstanceIdentifier<?>> s = Sets.newHashSet(node1, node2);
        Map<InstanceIdentifier<?>, ? extends CachingReadWriteTx> perNodeTransactions; //this map store every node transaction
        perNodeTransactions = Maps.toMap(s, new Function<InstanceIdentifier<?>, CachingReadWriteTx>() {
            @Nullable
            @Override
            public CachingReadWriteTx apply(@Nullable InstanceIdentifier<?> instanceIdentifier) {
                return instanceIdentifier == node1? cachingReadWriteTx1:cachingReadWriteTx2;
            }
        });

        RollbackImpl testRollBack = new RollbackImpl();
        CheckedFuture<Void, DTxException.RollbackFailedException> rollBackFut = testRollBack.rollback(perNodeTransactions, perNodeTransactions);

        try
        {
           rollBackFut.checkedGet();
        }catch (Exception e)
        {
           fail("get the unexpected exception from the rollback method");
        }

        int expectedDataNumInNode1Ident1 = 0, expectedDataNumInNode1Ident2 = 0, expectedDataNumInNode2Ident1 = 0, expectedDataNumInNode2Ident2 = 0;
        Assert.assertEquals("size of identifier1 data in transaction1 is wrong", expectedDataNumInNode1Ident1,testTransaction1.getTxDataSize(identifier1));
        Assert.assertEquals("size of identifier2 data in transaction1 is wrong", expectedDataNumInNode1Ident2,testTransaction1.getTxDataSize(identifier2));
        Assert.assertEquals("size of identifier1 data in transaction2 is wrong", expectedDataNumInNode2Ident1,testTransaction2.getTxDataSize(identifier1));
        Assert.assertEquals("size of identifier2 data in transaction2 is wrong", expectedDataNumInNode2Ident2,testTransaction2.getTxDataSize(identifier2));
    }

     /**
      *put data in node1
      *invoke rollback
      *delete exception occurs the rollback fail
      */
    @Test
    public void testRollbackFailWithDeleteException() {
        DTXTestTransaction testTransaction = new DTXTestTransaction();
        CachingReadWriteTx cachingReadWriteTx = new CachingReadWriteTx(testTransaction); // node1 caching transaction

        CheckedFuture<Void, DTxException> f = cachingReadWriteTx.asyncPut(LogicalDatastoreType.OPERATIONAL, identifier1,new TestData1());
        try
        {
            f.checkedGet();
            Assert.assertEquals(1, testTransaction.getTxDataSize(identifier1));
        }catch (Exception e)
        {
            fail("get unexpected exception from the AsyncPut");
        }

        //perNodeCaches a map store every node caching data
        Map<InstanceIdentifier<?>, CachingReadWriteTx> perNodeCaches = Maps.newHashMap();
        perNodeCaches.put(node1, cachingReadWriteTx);

        //delete exception rollback will fail
        testTransaction.setDeleteException(identifier1,true);

        Map<InstanceIdentifier<?>, ReadWriteTransaction> perNodeRollbackTxs = Maps.newHashMap();
        perNodeRollbackTxs.put(node1, testTransaction); // perNodeRollbackTx store each node transaction for rollback

        RollbackImpl testRollback = new RollbackImpl();
        CheckedFuture<Void, DTxException.RollbackFailedException> rollbackFuture =  testRollback.rollback(perNodeCaches,perNodeRollbackTxs);

        try{
            rollbackFuture.checkedGet();
            fail("can't get the exception from the failed rollback");
        }catch (Exception e)
        {
            Assert.assertTrue("type of exception is wrong", e instanceof DTxException.RollbackFailedException);
            System.out.println(e.getMessage());
        }
    }

    /**
     *put data in node1 and node2
     *invoke rollback
     *submit fail rollback fail
     */
    @Test
    public void testRollbackFailWithSubmitException() {
        DTXTestTransaction testTransaction1 = new DTXTestTransaction(); //node1 delegate transaction
        DTXTestTransaction testTransaction2 = new DTXTestTransaction(); //node2 delegate transaction

        final CachingReadWriteTx cachingReadWriteTx1 = new CachingReadWriteTx(testTransaction1); //nodeId1 caching transaction
        final CachingReadWriteTx cachingReadWriteTx2 = new CachingReadWriteTx(testTransaction2); //nodeId2 caching transaction

        CheckedFuture<Void, DTxException> f1 = cachingReadWriteTx1.asyncPut(LogicalDatastoreType.OPERATIONAL, identifier1, new TestData1());
        CheckedFuture<Void, DTxException> f2 = cachingReadWriteTx1.asyncPut(LogicalDatastoreType.OPERATIONAL, identifier2, new TestData2());
        CheckedFuture<Void, DTxException> f3 = cachingReadWriteTx2.asyncPut(LogicalDatastoreType.OPERATIONAL, identifier1, new TestData1());
        CheckedFuture<Void, DTxException> f4 = cachingReadWriteTx2.asyncPut(LogicalDatastoreType.OPERATIONAL, identifier2, new TestData2());

        try
        {
            f1.checkedGet();
            f2.checkedGet();
            f3.checkedGet();
            f4.checkedGet();

            //check if all the data has been put into the transactions
            Assert.assertEquals(1,testTransaction1.getTxDataSize(identifier1));
            Assert.assertEquals(1,testTransaction1.getTxDataSize(identifier2));
            Assert.assertEquals(1,testTransaction2.getTxDataSize(identifier1));
            Assert.assertEquals(1,testTransaction2.getTxDataSize(identifier2));
        }catch (Exception e)
        {
            fail("get the unexpected exception from the asyncPut");
        }

        //nodes set
        Set<InstanceIdentifier<?>> s = Sets.newHashSet(node1, node2);
        //map store every node transaction and the caching data
        Map<InstanceIdentifier<?>, ? extends CachingReadWriteTx> perNodeTransactions;

        perNodeTransactions = Maps.toMap(s, new Function<InstanceIdentifier<?>, CachingReadWriteTx>() {
            @Nullable
            @Override
            public CachingReadWriteTx apply(@Nullable InstanceIdentifier<?> instanceIdentifier) {
                return instanceIdentifier == node1? cachingReadWriteTx1:cachingReadWriteTx2;
            }
        });

        testTransaction2.setSubmitException(true);
        RollbackImpl testRollBack = new RollbackImpl();
        CheckedFuture<Void, DTxException.RollbackFailedException> rollBackFut = testRollBack.rollback(perNodeTransactions, perNodeTransactions);

        try
        {
            rollBackFut.checkedGet();
            fail("can't get the exception from the rollback");
        }catch (Exception e)
        {
            Assert.assertTrue("type of exception from the rollback is wrong", e instanceof DTxException.RollbackFailedException);
        }
    }
}
