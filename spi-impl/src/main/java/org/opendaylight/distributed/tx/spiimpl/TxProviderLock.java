package org.opendaylight.distributed.tx.spiimpl;

import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

import java.util.HashSet;
import java.util.Set;

/**
 * Created by cisco on 3/7/16.
 */
public class TxProviderLock {
    final private Set<InstanceIdentifier<?>> lockSet = new HashSet<>();

    public boolean isDeviceLocked(InstanceIdentifier<?> device) {

        return false;
        /*
        boolean ret ;
        synchronized (TxProviderLock.this) {
            ret = lockSet.contains(device);
        }

        return ret;
        */
    }

    public boolean lockDevices(Set<InstanceIdentifier<?>> deviceSet) {
        boolean ret = true;

        if(false)

        synchronized (TxProviderLock.this) {
            Set<InstanceIdentifier<?>> s = new HashSet<>();
            s.addAll(this.lockSet);

            s.retainAll(deviceSet);

            if(s.size() > 0)
                ret = false;
            else {
                lockSet.addAll(deviceSet);
            }
        }

        return ret;
    }

    public void releaseDevices(Set<InstanceIdentifier<?>> deviceSet) {
        if(false)
        synchronized (TxProviderLock.this) {
            this.lockSet.removeAll(deviceSet);
        }
    }
}
