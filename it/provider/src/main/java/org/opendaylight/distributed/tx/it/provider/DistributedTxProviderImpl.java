package org.opendaylight.distributed.tx.it.provider;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.CheckedFuture;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;

import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.opendaylight.controller.md.sal.binding.api.*;
import org.opendaylight.controller.md.sal.binding.api.DataChangeListener;
import org.opendaylight.controller.md.sal.common.api.data.*;
import org.opendaylight.distributed.tx.api.DTXLogicalTXProviderType;
import org.opendaylight.distributed.tx.api.DTx;
import org.opendaylight.distributed.tx.api.DTxProvider;
import org.opendaylight.yang.gen.v1.http.cisco.com.ns.yang.cisco.ios.xr.ifmgr.cfg.rev150107.InterfaceActive;
import org.opendaylight.yang.gen.v1.http.cisco.com.ns.yang.cisco.ios.xr.ifmgr.cfg.rev150107.InterfaceConfigurations;
import org.opendaylight.yang.gen.v1.http.cisco.com.ns.yang.cisco.ios.xr.ifmgr.cfg.rev150107._interface.configurations.InterfaceConfiguration;
import org.opendaylight.yang.gen.v1.http.cisco.com.ns.yang.cisco.ios.xr.ifmgr.cfg.rev150107._interface.configurations.InterfaceConfigurationBuilder;
import org.opendaylight.yang.gen.v1.http.cisco.com.ns.yang.cisco.ios.xr.ifmgr.cfg.rev150107._interface.configurations.InterfaceConfigurationKey;
import org.opendaylight.yang.gen.v1.http.cisco.com.ns.yang.cisco.ios.xr.ifmgr.oper.rev150107.InterfaceProperties;
import org.opendaylight.yang.gen.v1.http.cisco.com.ns.yang.cisco.ios.xr.ifmgr.oper.rev150107._interface.properties.DataNodes;
import org.opendaylight.yang.gen.v1.http.cisco.com.ns.yang.cisco.ios.xr.ifmgr.oper.rev150107._interface.properties.data.nodes.DataNode;
import org.opendaylight.yang.gen.v1.http.cisco.com.ns.yang.cisco.ios.xr.ifmgr.oper.rev150107._interface.table.Interfaces;
import org.opendaylight.yang.gen.v1.http.cisco.com.ns.yang.cisco.ios.xr.ifmgr.oper.rev150107._interface.table.interfaces.Interface;
import org.opendaylight.yang.gen.v1.http.cisco.com.ns.yang.cisco.xr.types.rev150119.InterfaceName;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.NetconfNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.network.topology.topology.topology.types.TopologyNetconf;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.distributed.tx.it.model.rev150105.*;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.distributed.tx.it.model.rev150105.ds.naive.rollback.data.DsNaiveRollbackDataEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.distributed.tx.it.model.rev150105.ds.naive.rollback.data.DsNaiveRollbackDataEntryBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.distributed.tx.it.model.rev150105.ds.naive.rollback.data.DsNaiveRollbackDataEntryKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.distributed.tx.it.model.rev150105.ds.naive.test.data.DsNaiveTestDataEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.distributed.tx.it.model.rev150105.ds.naive.test.data.DsNaiveTestDataEntryBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.distributed.tx.it.model.rev150105.ds.naive.test.data.DsNaiveTestDataEntryKey;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NetworkTopology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.TopologyId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.Topology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.TopologyKey;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.NodeKey;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.Identifier;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.binding.KeyedInstanceIdentifier;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;

public class DistributedTxProviderImpl implements DistributedTxItModelService, DataChangeListener {
    private static final Logger LOG = LoggerFactory.getLogger(DistributedTxProviderImpl.class);
    DTxProvider dTxProvider;
    private ListenerRegistration<DataChangeListener> dclReg;
    private DataBroker dataBroker;
    private MountPointService mountService;
    Set<NodeId> nodeIdSet = new HashSet<>();
    List<InterfaceName> nodeIfList = new ArrayList<>();
    private HashMap<NodeId,List<InterfaceName>> xrNodeIfLists = new HashMap<>();
    private DataBroker xrNodeBroker = null;

    public static final InstanceIdentifier<Topology> NETCONF_TOPO_IID = InstanceIdentifier
            .create(NetworkTopology.class).child(
                    Topology.class,
                    new TopologyKey(new TopologyId(TopologyNetconf.QNAME
                            .getLocalName())));

    private NodeId getNodeId(final InstanceIdentifier<?> path) {
        for (InstanceIdentifier.PathArgument pathArgument : path.getPathArguments()) {
            if (pathArgument instanceof InstanceIdentifier.IdentifiableItem<?, ?>) {

                final Identifier key = ((InstanceIdentifier.IdentifiableItem) pathArgument).getKey();
                if(key instanceof NodeKey) {
                    return ((NodeKey) key).getNodeId();
                }
            }
        }
        return null;
    }

    private boolean isTestIntf(String itfname) {
        if (itfname.contains("Giga") || itfname.contains("TenGig")) {
            return true;
        }
        return false;
    }

    private void initializeDsDataTree(){
        LOG.info("initilize ds data tree to data store");

        InstanceIdentifier<DsNaiveTestData> iid = InstanceIdentifier.create(DsNaiveTestData.class);

        if (this.dataBroker != null){
            dataBroker.registerDataChangeListener(LogicalDatastoreType.CONFIGURATION,
                    iid, new DsDataChangeListener(),
                    AsyncDataBroker.DataChangeScope.SUBTREE);
        }

        WriteTransaction transaction = dataBroker.newWriteOnlyTransaction();

        DsNaiveTestData dsTestData = new DsNaiveTestDataBuilder().build();

        transaction.put(LogicalDatastoreType.CONFIGURATION, iid, dsTestData);
        CheckedFuture<Void, TransactionCommitFailedException> cf = transaction.submit();

        Futures.addCallback(cf, new FutureCallback<Void>() {
            @Override
            public void onSuccess(@Nullable Void result) {
                LOG.info("initilize ds data tree to data store successfully");
            }

            @Override
            public void onFailure(Throwable t) {
                LOG.info("initilize ds data tree to data store failure");
            }
        });
    }

    @Override
    public Future<RpcResult<DsNaiveTestOutput>> dsNaiveTest(DsNaiveTestInput input) {
        InstanceIdentifier<DsNaiveTestData> iid = InstanceIdentifier.create(DsNaiveTestData.class);

        Set<InstanceIdentifier<?>> iidSet = new HashSet<>();
        iidSet.add(iid);

        //int numberOfInterfaces = input.getNumberofinterfaces();
        int numberOfInterfaces = 1;
        boolean testRollback = false;
        //boolean testRollback = input.isRollback();
        String name = input.getName();
        iidSet.add(iid);

        DTx itDtx = this.dTxProvider.newTx(iidSet);

        for (int i = 0; i < numberOfInterfaces; i++) {
            DsNaiveTestDataEntryBuilder testEntryBuilder = new DsNaiveTestDataEntryBuilder();

            testEntryBuilder.setName(name + Integer.toString(i));

            DsNaiveTestDataEntry data = testEntryBuilder.build();

            InstanceIdentifier<DsNaiveTestDataEntry> entryIid = InstanceIdentifier.create(DsNaiveTestData.class)
                    .child(DsNaiveTestDataEntry.class, new DsNaiveTestDataEntryKey(input.getName() + Integer.toString(i)));

            CheckedFuture<Void, ReadFailedException> cf = itDtx.putAndRollbackOnFailure(LogicalDatastoreType.CONFIGURATION, entryIid, data, iid);

            while (!cf.isDone()) ;
        }

        if (testRollback) {
            InstanceIdentifier<DsNaiveRollbackData> rollbackIid = InstanceIdentifier.create(DsNaiveRollbackData.class);

            DsNaiveRollbackDataEntryBuilder rollbackDataEntryBuilder = new DsNaiveRollbackDataEntryBuilder();
            DsNaiveRollbackDataEntry rolbackDataEntry = rollbackDataEntryBuilder.build();

            InstanceIdentifier<DsNaiveRollbackDataEntry> rollbackEntryIid = InstanceIdentifier.create(DsNaiveRollbackData.class)
                    .child(DsNaiveRollbackDataEntry.class, new DsNaiveRollbackDataEntryKey(input.getName()));

            CheckedFuture<Void, ReadFailedException> cf = itDtx.putAndRollbackOnFailure(LogicalDatastoreType.CONFIGURATION, rollbackEntryIid, rolbackDataEntry, iid);
        }

        if (!testRollback)
            itDtx.submit();

        DsNaiveTestOutput output = new DsNaiveTestOutputBuilder().setResult("Bingo").build();

        return Futures.immediateFuture(RpcResultBuilder.success(output).build());
    }

    @Override
    public Future<RpcResult<MixedNaiveTestOutput>> mixedNaiveTest(MixedNaiveTestInput input) {
        List<NodeId> nodeIdList = new ArrayList(this.nodeIdSet);
        boolean testRollback = false;
        String name = input.getName();

        if(name.length() > 5)
            testRollback = true;

        int number = new Random().nextInt(100);
        int keyNumber = number;

        InstanceIdentifier msNodeId = NETCONF_TOPO_IID.child(Node.class, new NodeKey(nodeIdList.get(0)));

        Set<InstanceIdentifier<?>> txIidSet = new HashSet<>();
        txIidSet.add(msNodeId);

        InstanceIdentifier<InterfaceConfigurations> netconfIid = InstanceIdentifier.create(InterfaceConfigurations.class);

        InterfaceName ifname = this.nodeIfList.get(0);

        final KeyedInstanceIdentifier<InterfaceConfiguration, InterfaceConfigurationKey> specificInterfaceCfgIid
                = netconfIid.child(InterfaceConfiguration.class, new InterfaceConfigurationKey(new InterfaceActive("act"), ifname));

        final InterfaceConfigurationBuilder interfaceConfigurationBuilder = new InterfaceConfigurationBuilder();
        interfaceConfigurationBuilder.setInterfaceName(ifname);
        interfaceConfigurationBuilder.setDescription("Test description" + "-" + input.getName() + "-" + Integer.toString(number));
        interfaceConfigurationBuilder.setActive(new InterfaceActive("act"));

        InterfaceConfiguration config = interfaceConfigurationBuilder.build();
        LOG.info("dtx ifc {}", ifname.toString());

        InstanceIdentifier<DsNaiveTestData> dataStoreNodeId = InstanceIdentifier.create(DsNaiveTestData.class);

        Map<DTXLogicalTXProviderType, Set<InstanceIdentifier<?>>> m = new HashMap<>();
        m.put(DTXLogicalTXProviderType.NETCONF_TX_PROVIDER, txIidSet);
        Set<InstanceIdentifier<?>> dataStoreSet = new HashSet<>();
        dataStoreSet.add(dataStoreNodeId);
        m.put(DTXLogicalTXProviderType.DATASTORE_TX_PROVIDER, dataStoreSet);

        DTx itDtx = this.dTxProvider.newTx(m);

        CheckedFuture<Void, ReadFailedException> done = itDtx.putAndRollbackOnFailure(DTXLogicalTXProviderType.NETCONF_TX_PROVIDER,
                LogicalDatastoreType.CONFIGURATION, specificInterfaceCfgIid, config, msNodeId);

        while (!done.isDone()) {
            Thread.yield();

            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        boolean doSumbit = true;

        try {
            done.get();
        } catch (InterruptedException e) {
            e.printStackTrace();
            doSumbit = false;
        } catch (ExecutionException e) {
            e.printStackTrace();
            doSumbit = false;
        }

        LOG.info("now writing data store");

        DsNaiveTestDataEntryBuilder testEntryBuilder = new DsNaiveTestDataEntryBuilder();

        testEntryBuilder.setName(name + Integer.toString(number));

        DsNaiveTestDataEntry data = testEntryBuilder.build();

        if (testRollback) {
            keyNumber = 101;
        }
        InstanceIdentifier<DsNaiveTestDataEntry> entryIid = InstanceIdentifier.create(DsNaiveTestData.class)
                .child(DsNaiveTestDataEntry.class, new DsNaiveTestDataEntryKey(input.getName() + Integer.toString(keyNumber)));

        CheckedFuture<Void, ReadFailedException> cf = itDtx.putAndRollbackOnFailure(DTXLogicalTXProviderType.DATASTORE_TX_PROVIDER,
                LogicalDatastoreType.CONFIGURATION, entryIid, data, dataStoreNodeId);

        while(!cf.isDone()){Thread.yield();}

        if(doSumbit) {
            try {
                done.get();
            } catch (InterruptedException e) {
                e.printStackTrace();
                doSumbit = false;
            } catch (ExecutionException e) {
                e.printStackTrace();
                doSumbit = false;
            }
        }
        if(!testRollback) {
            if (doSumbit) {
                CheckedFuture<Void, TransactionCommitFailedException> submitFuture = itDtx.submit();
                LOG.info("submit done");
            } else {
                LOG.info("put failure. no submit");
            }
        }

        MixedNaiveTestOutput output = new MixedNaiveTestOutputBuilder().setResult("Bingo").build();

        return Futures.immediateFuture(RpcResultBuilder.success(output).build());
    }

    @Override
    public Future<RpcResult<NaiveTestOutput>> naiveTest(NaiveTestInput input) {
        List<NodeId> nodeIdList = new ArrayList(this.nodeIdSet);
        int numberOfInterfaces = 1;
        // int numberOfInterfaces = input.getNumberofinterfaces();
        boolean testRollback = false;
        // boolean testRollback = input.isRollback();
        String name = input.getName();

        InstanceIdentifier msNodeId = NETCONF_TOPO_IID.child(Node.class, new NodeKey(nodeIdList.get(0)));

        Set<InstanceIdentifier<?>> txIidSet = new HashSet<>();
        txIidSet.add(msNodeId);
        InstanceIdentifier<InterfaceConfigurations> iid = InstanceIdentifier.create(InterfaceConfigurations.class);

        for(int i = 0; i < numberOfInterfaces; i++) {
            InterfaceName ifname = this.nodeIfList.get(i);

            final KeyedInstanceIdentifier<InterfaceConfiguration, InterfaceConfigurationKey> specificInterfaceCfgIid
                    = iid.child(InterfaceConfiguration.class,
                    new InterfaceConfigurationKey(new InterfaceActive("act"), ifname));

            final InterfaceConfigurationBuilder interfaceConfigurationBuilder = new InterfaceConfigurationBuilder();
            interfaceConfigurationBuilder.setInterfaceName(ifname);
            interfaceConfigurationBuilder.setDescription("Test description" + "-" + input.getName());
            interfaceConfigurationBuilder.setActive(new InterfaceActive("act"));

            InterfaceConfiguration config = interfaceConfigurationBuilder.build();

            LOG.info("dtx ifc {}", ifname.toString());

            if (name.length() < 6) {
                final ReadWriteTransaction xrNodeReadTx = xrNodeBroker.newReadWriteTransaction();

                xrNodeReadTx.put(LogicalDatastoreType.CONFIGURATION, specificInterfaceCfgIid, config);

                final CheckedFuture<Void, TransactionCommitFailedException> submit = xrNodeReadTx
                        .submit();

                Futures.addCallback(submit, new FutureCallback<Void>() {
                    @Override
                    public void onSuccess(final Void result) {
                        LOG.info("Success to commit interface changes {} ");
                    }

                    @Override
                    public void onFailure(final Throwable t) {
                        LOG.info("Fail to commit interface changes");
                    }
                });
            } else {
                DTx itDtx = this.dTxProvider.newTx(txIidSet);

                CheckedFuture<Void, ReadFailedException> done = itDtx.putAndRollbackOnFailure(LogicalDatastoreType.CONFIGURATION, specificInterfaceCfgIid, config, msNodeId);

                int cnt = 0;

                while (!done.isDone()) {
                    Thread.yield();

                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }

                boolean doSumbit = true;

                try {
                    done.get();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    doSumbit = false;
                } catch (ExecutionException e) {
                    e.printStackTrace();
                    doSumbit = false;
                }

                if (doSumbit) {
                    CheckedFuture<Void, TransactionCommitFailedException> submitFuture = itDtx.submit();
                    LOG.info("submit done");
                } else {
                    LOG.info("put failure. no submit");
                }
            }
        }

        NaiveTestOutput output = new NaiveTestOutputBuilder().setResult("Bingo").build();

        return Futures.immediateFuture(RpcResultBuilder.success(output).build());
    }

    public DistributedTxProviderImpl(DTxProvider provider, DataBroker db, MountPointService ms){
        this.dTxProvider = provider;
        this.dataBroker = db;
        this.mountService = ms;
        this.dclReg = dataBroker.registerDataChangeListener(
                LogicalDatastoreType.OPERATIONAL,
                NETCONF_TOPO_IID.child(Node.class), this,
                AsyncDataBroker.DataChangeScope.SUBTREE);

        this.initializeDsDataTree();
    }

    @Override
    public void onDataChanged(AsyncDataChangeEvent<InstanceIdentifier<?>, DataObject> change) {
        DataObject dataObject = change.getUpdatedSubtree();

        LOG.info("NetconftestProvider onDataChange, change: {} ", dataObject);

        for ( Map.Entry<InstanceIdentifier<?>,
                        DataObject> entry : change.getCreatedData().entrySet()) {
            if (entry.getKey().getTargetType() == NetconfNode.class) {
                NodeId nodeId = getNodeId(entry.getKey());
                LOG.info("NETCONF Node: {} was created", nodeId.getValue());

                // Not much can be done at this point, we need UPDATE event with
                // state set to connected
            }
        }

        for ( Map.Entry<InstanceIdentifier<?>,
                        DataObject> entry : change.getUpdatedData().entrySet()) {
            if (entry.getKey().getTargetType() == NetconfNode.class) {
                NodeId nodeId = getNodeId(entry.getKey());

                InstanceIdentifier msNodeId = NETCONF_TOPO_IID.child(Node.class, new NodeKey(nodeId));

                NetconfNode nnode = (NetconfNode)entry.getValue();
                LOG.info("NETCONF Node: {} is fully connected", nodeId.getValue());

                InstanceIdentifier<DataNodes> iid = InstanceIdentifier.create(
                             InterfaceProperties.class).child(DataNodes.class);

                final Optional<MountPoint> xrNodeOptional = mountService
                                .getMountPoint(NETCONF_TOPO_IID.child(Node.class,
                                        new NodeKey(nodeId)));
                final MountPoint xrNode = xrNodeOptional.get();

                if(xrNodeBroker == null) {
                    xrNodeBroker = xrNode.getService(DataBroker.class).get();
                    this.nodeIdSet.add(nodeId);
                }

                final ReadOnlyTransaction xrNodeReadTx = xrNodeBroker
                        .newReadOnlyTransaction();
                Optional<DataNodes> ldn;
                try {
                    ldn = xrNodeReadTx.read(LogicalDatastoreType.OPERATIONAL, iid).checkedGet();
                } catch (ReadFailedException e) {
                    throw new IllegalStateException(
                            "Unexpected error reading data from " + nodeId.getValue(), e);
                }
                if (ldn.isPresent()) {
                    LOG.info("interfaces: {}", ldn.get());
                    List<DataNode> dataNodes = ldn.get().getDataNode();
                    for (DataNode node : dataNodes) {

                        LOG.info("DataNode '{}'", node.getDataNodeName().getValue());

                        Interfaces ifc = node.getSystemView().getInterfaces();
                        List<Interface> ifList = ifc.getInterface();
                        for (Interface intf : ifList) {
                            if (isTestIntf(intf.getInterfaceName().toString())) {
                                LOG.info("add interface {}", intf.getInterfaceName().toString());
                                this.nodeIfList.add(intf.getInterfaceName());
                            }
                        }
                    }
                    //put new node's intfaces
                    this.xrNodeIfLists.put(nodeId, nodeIfList);
                }
            }
        }
    }

    private class DsDataChangeListener implements DataChangeListener{
        @Override
        public void onDataChanged(AsyncDataChangeEvent<InstanceIdentifier<?>, DataObject> asyncDataChangeEvent) {
            DataObject dataObject = asyncDataChangeEvent.getUpdatedSubtree();

            LOG.info("DS DsDataChangeListenClass on changed called {}", dataObject);
        }
    }
}
