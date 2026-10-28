package dev.cerez.titan.log;

import ch.qos.logback.core.AppenderBase;

import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;

public class HistoryAppender<E> extends AppenderBase<E> {

    private final Deque<E> history = new ConcurrentLinkedDeque<>();
    private static final int MAX_LOGS = 500;

    @Override
    protected void append(E event) {
        history.addLast(event);
        while (history.size() > MAX_LOGS) {
            history.pollFirst();
        }
    }

    public List<E> getHistory() {
        return new ArrayList<>(history);
    }
}
