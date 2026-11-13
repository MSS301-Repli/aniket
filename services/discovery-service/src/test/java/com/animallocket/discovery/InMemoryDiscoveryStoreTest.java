package com.animallocket.discovery;

import com.animallocket.discovery.store.DiscoveryStore;
import com.animallocket.discovery.store.InMemoryDiscoveryStore;

class InMemoryDiscoveryStoreTest extends DiscoveryStoreContractTest {
    @Override
    DiscoveryStore newStore() {
        return new InMemoryDiscoveryStore();
    }
}
