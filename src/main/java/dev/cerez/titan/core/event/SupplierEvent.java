package dev.cerez.titan.core.event;

public interface SupplierEvent<E extends Listener> {


    void registerListener(E e);

}
