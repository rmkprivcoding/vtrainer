package vtrainer.util;

import java.awt.event.ActionEvent;
import java.util.EventListener;

public interface DeferredChangeListener extends EventListener{
    public void applyChangesIssued(ActionEvent e);
    public void resetIssued(ActionEvent e);
}
