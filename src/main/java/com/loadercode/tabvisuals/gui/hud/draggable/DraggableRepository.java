/*© 2026 LoxeDLC. All rights reserved.*/
package com.loadercode.tabvisuals.gui.hud.draggable;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;

import com.loadercode.tabvisuals.gui.hud.elements.*;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class DraggableRepository {
    // CopyOnWriteArrayList: HUD-элементы добавляются/удаляются на лету
    // (CoolDowns создаёт элемент на каждый кулдаун и удаляет его после),
    // а отрисовка и обработка пакетов перебирают этот список. Обычный
    // ArrayList при этом кидал ConcurrentModificationException.
    List<AbstractDraggable> draggable = new CopyOnWriteArrayList<>();

    public void setup() {
        register(
                new TargetHud(),
                new Potions(),
                new HotKeys(),
                new Watermark(),
                new Inventory(),
                new CoolDowns(),
                new Binds(),
                new PlayerInfo(),
                new Scoreboard(),
                new MediaPlayer(),
                new Armor(),
                new HotBar(),
                new Saturation(),
                new TotemCounterHud(),
                new KeystrokesOverlayHud(),
                new EPRender(),
                new com.loadercode.tabvisuals.gui.hud.elements.island.DynamicIsland()
        );
    }

    public void register(AbstractDraggable... module) {
        draggable.addAll(List.of(module));
    }

    public List<AbstractDraggable> draggable() {
        return draggable;
    }

    /** Есть ли элемент с таким именем (регистронезависимо) — для prune в HudEditor. */
    public boolean has_name(String name) {
        if (name == null) {
            return false;
        }
        for (AbstractDraggable d : draggable) {
            if (d.getName().equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

    public <T extends AbstractDraggable> T get(String name) {
        return draggable.stream()
                .filter(module -> module.getName().equalsIgnoreCase(name))
                .map(module -> (T) module)
                .findFirst()
                .orElse(null);
    }

    public <T extends AbstractDraggable> T get(Class<T> clazz) {
        return draggable.stream()
                .filter(module -> clazz.isAssignableFrom(module.getClass()))
                .map(clazz::cast)
                .findFirst()
                .orElse(null);
    }
}
