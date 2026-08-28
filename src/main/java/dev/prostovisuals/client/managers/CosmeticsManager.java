package dev.prostovisuals.client.managers;

import dev.prostovisuals.client.custommodels.CosmeticEntry;
import dev.prostovisuals.client.custommodels.FiguraCosmeticsEngine;
import dev.prostovisuals.client.events.impl.EventTick;
import dev.prostovisuals.client.util.Wrapper;
import dev.prostovisuals.prostovisuals;
import meteordevelopment.orbit.EventHandler;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/** Always-on cosmetic selection manager used by the integrated ClickGUI Cosmetics page. */
public final class CosmeticsManager implements Wrapper {
    private final Path stateFile = mc.runDirectory.toPath().resolve("prostovisuals").resolve("selected_cosmetic.dat");
    private final Path petStateFile = mc.runDirectory.toPath().resolve("prostovisuals").resolve("pet_behavior.dat");
    private String selectedPath = "";
    private PetBehavior petBehavior = PetBehavior.FOLLOW;
    private String lastApplied = "";
    private UUID lastPlayer;
    private long nextRetryAt;

    public CosmeticsManager() {
        load();
        loadPetBehavior();
        prostovisuals.getInstance().getEventHandler().subscribe(this);
        FiguraCosmeticsEngine.ensureInstalled();
        if (hasSelection() && FiguraCosmeticsEngine.findByRelativePath(selectedPath) == null) {
            selectedPath = "";
            save();
        }
    }

    public String getSelectedPath() { return selectedPath; }
    public boolean hasSelection() { return selectedPath != null && !selectedPath.isBlank(); }

    public boolean isSelected(CosmeticEntry entry) {
        return entry != null && hasSelection() && selectedPath.equalsIgnoreCase(entry.relativePath());
    }

    public PetBehavior getPetBehavior() { return petBehavior; }

    public void setPetBehavior(PetBehavior behavior) {
        if (behavior == null || behavior == petBehavior) return;
        petBehavior = behavior;
        savePetBehavior();
        CosmeticEntry selected = FiguraCosmeticsEngine.findByRelativePath(selectedPath);
        if (selected != null && selected.kind() == CosmeticEntry.Kind.PET) {
            writePetConfig(selected);
            lastApplied = "";
            FiguraCosmeticsEngine.clearLocal();
            applyNow();
        }
    }

    public void toggle(CosmeticEntry entry) {
        if (entry == null) return;
        if (isSelected(entry)) {
            clear();
            return;
        }
        selectedPath = entry.relativePath();
        lastApplied = "";
        save();
        applyNow();
    }

    public void clear() {
        selectedPath = "";
        lastApplied = "";
        lastPlayer = null;
        FiguraCosmeticsEngine.clearLocal();
        save();
    }

    private void load() {
        try {
            if (Files.isRegularFile(stateFile)) selectedPath = Files.readString(stateFile, StandardCharsets.UTF_8).trim();
        } catch (Throwable ignored) { selectedPath = ""; }
    }

    private void save() {
        try {
            Files.createDirectories(stateFile.getParent());
            Files.writeString(stateFile, selectedPath == null ? "" : selectedPath, StandardCharsets.UTF_8);
        } catch (Throwable ignored) {}
    }

    private void loadPetBehavior() {
        try {
            if (Files.isRegularFile(petStateFile)) {
                petBehavior = PetBehavior.valueOf(Files.readString(petStateFile, StandardCharsets.UTF_8).trim());
            }
        } catch (Throwable ignored) { petBehavior = PetBehavior.FOLLOW; }
    }

    private void savePetBehavior() {
        try {
            Files.createDirectories(petStateFile.getParent());
            Files.writeString(petStateFile, petBehavior.name(), StandardCharsets.UTF_8);
        } catch (Throwable ignored) {}
    }

    private void writePetConfig(CosmeticEntry entry) {
        if (entry == null || entry.directory() == null || entry.kind() != CosmeticEntry.Kind.PET) return;
        try {
            Path cfg = entry.directory().resolve("pv_pet_config.lua");
            String lua = "return { behavior = \"" + petBehavior.luaName + "\" }\n";
            Files.writeString(cfg, lua, StandardCharsets.UTF_8);
        } catch (Throwable ignored) {}
    }

    @EventHandler
    public void onTick(EventTick e) {
        if (mc.player == null || mc.world == null || !hasSelection()) return;
        UUID uuid = mc.player.getUuid();
        if (!uuid.equals(lastPlayer)) {
            lastPlayer = uuid;
            lastApplied = "";
        }
        if (!selectedPath.equals(lastApplied) && System.currentTimeMillis() >= nextRetryAt) applyNow();
    }

    private void applyNow() {
        if (mc.player == null || !hasSelection()) return;
        CosmeticEntry entry = FiguraCosmeticsEngine.findByRelativePath(selectedPath);
        if (entry != null) writePetConfig(entry);
        if (entry != null && FiguraCosmeticsEngine.applyLocal(entry)) lastApplied = selectedPath;
        else nextRetryAt = System.currentTimeMillis() + 1500L;
    }

    public enum PetBehavior {
        FOLLOW("follow", "Follow"),
        FRONT("front", "In front"),
        ORBIT("orbit", "Orbit"),
        ASSIST("assist", "Assist target");

        private final String luaName;
        private final String label;
        PetBehavior(String luaName, String label) { this.luaName = luaName; this.label = label; }
        public String label() { return label; }
    }
}
