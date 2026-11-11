package dev.cerez.titan.utils;

public final class FinalRefence<R> {

    public R ref = null;
    private volatile boolean isLocked = false;

    public synchronized void set(R r){
        if (isLocked) {
            isLocked = false;
            return;
        }
        this.ref = r;
    }

    public R get(){
        return ref;
    }

    public R getIfAbsent(R newRef){
        if (ref == null) {
            set(newRef);
            return newRef;
        }else {
            return ref;
        }
    }
}
