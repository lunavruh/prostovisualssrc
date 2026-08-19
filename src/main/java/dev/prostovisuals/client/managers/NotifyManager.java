package dev.prostovisuals.client.managers;

import com.google.common.collect.Lists;
import dev.prostovisuals.prostovisuals;
import dev.prostovisuals.client.events.impl.EventRender2D;
import dev.prostovisuals.modules.api.Module;
import dev.prostovisuals.client.util.Wrapper;
import dev.prostovisuals.client.util.notify.Notify;
import meteordevelopment.orbit.EventHandler;

import java.util.*;

public class NotifyManager implements Wrapper {

    public NotifyManager() {
        prostovisuals.getInstance().getEventHandler().subscribe(this);
    }

    private final List<Notify> notifies = new ArrayList<>();

    public void add(Notify notify) {
        notifies.add(notify);
    }

    @EventHandler
    public void onRender2D(EventRender2D e) {
        if (notifies.isEmpty()) return;
        float startY = mc.getWindow().getScaledHeight() / 2f + 26;
        if (notifies.size() > 10) notifies.removeFirst();
        notifies.removeIf(Notify::expired);

        for (Notify notify : Lists.newArrayList(notifies)) {
            startY = (startY - 16f);
            notify.render(e, startY + (notifies.size() * 16f));
        }
    }
}