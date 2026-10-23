package dev.cerez.titan.core.event;

public interface SupplierEvent<E extends BaseListener> {

    void registerListener(E e);

}
