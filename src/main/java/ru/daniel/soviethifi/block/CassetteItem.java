package ru.daniel.soviethifi.block;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import ru.daniel.soviethifi.network.Network;

public final class CassetteItem extends Item {
    public CassetteItem(Properties properties) { super(properties); }
    public static String track(ItemStack stack) { return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getStringOr("track", ""); }
    public static void record(ItemStack stack, String hash, String title) {
        if (stack.getCount() != 1) throw new IllegalArgumentException("Record one cassette at a time");
        CompoundTag data = new CompoundTag(); data.putString("track", hash);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(title));
        stack.set(DataComponents.MAX_STACK_SIZE, 1);
    }
    /** Consume exactly one cassette, preserving the rest and handling a full inventory. */
    public static boolean finishRecording(Player player, InteractionHand hand, ItemStack expected, String hash, String title) {
        if (expected.isEmpty() || player.getItemInHand(hand) != expected || !(expected.getItem() instanceof CassetteItem)) return false;
        ItemStack recorded = expected.copyWithCount(1);
        record(recorded, hash, title);
        if (expected.getCount() == 1) player.setItemInHand(hand, recorded);
        else {
            expected.shrink(1);
            if (!player.getInventory().add(recorded)) player.drop(recorded, false);
        }
        player.inventoryMenu.broadcastChanges();
        return true;
    }
    @Override public void inventoryTick(ItemStack stack, net.minecraft.server.level.ServerLevel level,
            net.minecraft.world.entity.Entity entity, net.minecraft.world.entity.EquipmentSlot slot) {
        // Recorded cassettes from older versions did not need an explicit component.
        if (!track(stack).isEmpty()) stack.set(DataComponents.MAX_STACK_SIZE, 1);
    }
    @Override public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer serverPlayer) Network.send(serverPlayer, new Network.OpenUi(0, player.blockPosition(), hand == InteractionHand.OFF_HAND));
        return InteractionResult.SUCCESS;
    }
}
