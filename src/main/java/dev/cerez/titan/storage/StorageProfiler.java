package dev.cerez.titan.storage;

import dev.cerez.titan.core.BaseConfig;
import dev.cerez.titan.core.BasePersistent;

public record StorageProfiler<C extends BaseConfig, P extends BasePersistent>(Storage<C> config, Storage<P> persistent) {

}
