package dev.cerez.titan.core.event;

public interface SupplierEvent<E extends Event> {

    E getEvent();

    void setEvent(E e);

}
