package ru.daniel.soviethifi.block;

import net.minecraft.core.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.*;
import ru.daniel.soviethifi.SovietHiFi;
import ru.daniel.soviethifi.server.HiFiServer;
import java.util.*;

public final class EquipmentEntity extends BlockEntity {
    public final List<ItemStack> tapes = new ArrayList<>();
    public boolean playing;
    public long started, pausedTicks;
    public float volume = 0.65f, tape = 0.35f;
    public boolean moving;
    public EquipmentEntity(BlockPos pos, BlockState state) { super(SovietHiFi.EQUIPMENT.get(), pos, state); }
    public EquipmentBlock.Kind kind() { return ((EquipmentBlock)getBlockState().getBlock()).kind; }
    public boolean supports(EquipmentBlock.Kind kind) {
        return kind() == kind || kind() == EquipmentBlock.Kind.STACK &&
            (kind == EquipmentBlock.Kind.DECK || kind == EquipmentBlock.Kind.AMP);
    }
    public void copyPlaybackTo(EquipmentEntity target) {
        target.tapes.clear(); tapes.forEach(s -> target.tapes.add(s.copy()));
        target.playing = playing; target.started = started; target.pausedTicks = pausedTicks;
        target.volume = volume; target.tape = tape;
        target.setComponents(components());
        target.changed();
    }
    public static void tick(Level level, BlockPos pos, BlockState state, EquipmentEntity entity) {
        if (entity.supports(EquipmentBlock.Kind.DECK) && level instanceof ServerLevel sl) HiFiServer.get(sl.getServer()).track(entity);
    }
    public void insert(Player player, ItemStack stack) {
        int capacity = kind() == EquipmentBlock.Kind.RACK ? 12 : 1;
        if (tapes.size() >= capacity) { player.sendOverlayMessage(Component.literal("Сначала извлеките кассету: Shift + ПКМ")); return; }
        if (supports(EquipmentBlock.Kind.DECK) && CassetteItem.track(stack).isEmpty()) {
            player.sendOverlayMessage(Component.literal("Сначала запишите трек: ПКМ с кассетой в руке")); return;
        }
        tapes.add(stack.split(1)); pausedTicks = 0;
        if (supports(EquipmentBlock.Kind.DECK) && level instanceof ServerLevel sl) HiFiServer.get(sl.getServer()).play(this, player);
        changed();
    }
    public void eject(Player player) { ejectAt(player, tapes.size() - 1); }
    public void ejectAt(Player player, int index) {
        if (index < 0 || index >= tapes.size()) return;
        if (supports(EquipmentBlock.Kind.DECK)) { playing = false; pausedTicks = 0; }
        ItemStack stack = tapes.remove(index);
        if (!player.getInventory().add(stack)) player.drop(stack, false);
        changed();
    }
    public String trackId() { return tapes.isEmpty() ? "" : CassetteItem.track(tapes.getFirst()); }
    public void changed() {
        setChanged();
        if (level != null) level.setBlock(worldPosition, getBlockState().setValue(EquipmentBlock.POWERED, playing).setValue(EquipmentBlock.TAPES, tapes.size()), 3);
    }
    @Override protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        var list = out.list("tapes", ItemStack.CODEC); tapes.forEach(list::add);
        // Persist position but never resume unattended after a server restart or chunk unload.
        out.putLong("position", playing && level != null ? Math.max(0, level.getGameTime() - started) : pausedTicks);
        out.putFloat("volume", volume); out.putFloat("tape", tape);
    }
    @Override protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in); tapes.clear();
        for (ItemStack stack : in.listOrEmpty("tapes", ItemStack.CODEC)) if (tapes.size() < (kind() == EquipmentBlock.Kind.RACK ? 12 : 1)) tapes.add(stack);
        pausedTicks = Math.max(0, in.getLongOr("position", 0)); playing = false;
        volume = Math.clamp(in.getFloatOr("volume", 0.65f), 0, 1); tape = Math.clamp(in.getFloatOr("tape", 0.35f), 0, 1);
    }
    @Override public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (!moving && level != null && !level.isClientSide()) { for (ItemStack stack : tapes) Block.popResource(level, pos, stack); tapes.clear(); }
        super.preRemoveSideEffects(pos, state);
    }
}
