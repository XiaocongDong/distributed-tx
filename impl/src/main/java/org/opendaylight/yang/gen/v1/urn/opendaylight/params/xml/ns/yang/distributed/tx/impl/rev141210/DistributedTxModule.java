package org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.distributed.tx.impl.rev141210;

import org.opendaylight.distributed.tx.impl.spi.DTxProviderImpl;
import org.opendaylight.distributed.tx.spi.TxProvider;

public class DistributedTxModule extends org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.distributed.tx.impl.rev141210.AbstractDistributedTxModule {
    private TxProvider txProvider;

    public void addProvider(final TxProvider txProvider) {
        this.txProvider = txProvider;
    }

    public DistributedTxModule(org.opendaylight.controller.config.api.ModuleIdentifier identifier, org.opendaylight.controller.config.api.DependencyResolver dependencyResolver) {
        super(identifier, dependencyResolver);
    }

    public DistributedTxModule(org.opendaylight.controller.config.api.ModuleIdentifier identifier, org.opendaylight.controller.config.api.DependencyResolver dependencyResolver, org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.distributed.tx.impl.rev141210.DistributedTxModule oldModule, java.lang.AutoCloseable oldInstance) {
        super(identifier, dependencyResolver, oldModule, oldInstance);
    }

    @Override
    public void customValidation() {
        // add custom validation form module attributes here.
    }

    @Override
    public java.lang.AutoCloseable createInstance() {
        final DTxProviderImpl dTxProvider = new DTxProviderImpl(txProvider);
        getBrokerDependency().registerConsumer(dTxProvider);
        return dTxProvider;
    }
}
