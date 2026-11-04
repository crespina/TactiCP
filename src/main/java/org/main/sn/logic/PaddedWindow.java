package org.main.sn.logic;

import org.maxicp.cp.engine.core.AbstractCPConstraint;
import org.maxicp.cp.engine.core.CPIntervalVar;

/**
 * {@code padded = [start(window), end(window) + pad)} for two optional intervals with the same
 * presence (posted separately, e.g. as {@code eq(padded.status(), window.status())}).
 * <p>
 * Since both are present or both absent, the bounds can be copied both ways while the presence is
 * still undecided: they only matter if both turn out present, and one that no longer fits becomes
 * absent, taking the other with it. Unlike {@code present => start(window) = start(padded)} on
 * {@code startOr} views, this posts no reified equality between variables.
 */
public class PaddedWindow extends AbstractCPConstraint {

    private final CPIntervalVar window;
    private final CPIntervalVar padded;
    private final int pad;

    public PaddedWindow(CPIntervalVar window, CPIntervalVar padded, int pad) {
        super(window.getSolver());
        this.window = window;
        this.padded = padded;
        this.pad = pad;
    }

    @Override
    public void post() {
        window.propagateOnChange(this);
        padded.propagateOnChange(this);
        propagate();
    }

    @Override
    public void propagate() {
        if (window.isAbsent() || padded.isAbsent()) {
            setActive(false);
            return;
        }
        padded.setStartMin(window.startMin());
        padded.setStartMax(window.startMax());
        padded.setEndMin(window.endMin() + pad);
        padded.setEndMax(window.endMax() + pad);
        padded.setLengthMin(window.lengthMin() + pad);
        padded.setLengthMax(window.lengthMax() + pad);
        window.setStartMin(padded.startMin());
        window.setStartMax(padded.startMax());
        window.setEndMin(padded.endMin() - pad);
        window.setEndMax(padded.endMax() - pad);
        window.setLengthMin(padded.lengthMin() - pad);
        window.setLengthMax(padded.lengthMax() - pad);
    }
}
