package de.vier_bier.habpanelviewer.openhab;

/**
 * Interface to be notified when the web view enters and exits a habpanel URL.
 */
public interface IHabPanelListener {
    void inside(String url);

    void outside(String url);
}
