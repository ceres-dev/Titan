package dev.cerez.titan.utils;

/**
 * Subscribe a un evento subscription o rutina, extiende de {@link Revocable} ya que debe ser revocable
 *
 * @see Revocable
 */
public interface Subscribable extends Revocable {

    void subscribe();
}
