package ru.daniel.soviethifi;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import ru.daniel.soviethifi.block.*;
import ru.daniel.soviethifi.network.Network;
import ru.daniel.soviethifi.server.HiFiServer;
import java.util.function.Supplier;

public final class SovietHiFi implements ModInitializer {
    public static final String ID = "soviet_hifi";
    public static Identifier id(String name) { return Identifier.fromNamespaceAndPath(ID, name); }
    private static <T> Supplier<T> fixed(T value) { return () -> value; }
    public static final Supplier<Block> SPEAKER = equipment("s90", EquipmentBlock.Kind.SPEAKER);
    public static final Supplier<Block> DECK = equipment("mayak", EquipmentBlock.Kind.DECK);
    public static final Supplier<Block> AMP = equipment("amfiton", EquipmentBlock.Kind.AMP);
    public static final Supplier<Block> RACK = equipment("cassette_rack", EquipmentBlock.Kind.RACK);
    public static final Supplier<Block> STACK = fixed(Registry.register(BuiltInRegistries.BLOCK, id("stereo_stack"),
        new StackedEquipmentBlock(BlockBehaviour.Properties.of().setId(ResourceKey.create(Registries.BLOCK, id("stereo_stack")))
            .mapColor(MapColor.WOOD).strength(1.5f).noOcclusion())));
    public static final Supplier<Item> CASSETTE = fixed(Registry.register(BuiltInRegistries.ITEM, id("cassette"),
        new CassetteItem(new Item.Properties().setId(ResourceKey.create(Registries.ITEM, id("cassette"))).stacksTo(64))));
    public static final Supplier<BlockEntityType<EquipmentEntity>> EQUIPMENT = fixed(Registry.register(
        BuiltInRegistries.BLOCK_ENTITY_TYPE, id("equipment"), FabricBlockEntityTypeBuilder.create(
            EquipmentEntity::new, SPEAKER.get(), DECK.get(), AMP.get(), RACK.get(), STACK.get()).build()));
    public static final Supplier<CreativeModeTab> TAB = fixed(Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB,
        id("studio"), FabricCreativeModeTab.builder().title(Component.translatable("itemGroup.soviet_hifi"))
        .icon(() -> new ItemStack(SPEAKER.get()))
        .displayItems((_, out) -> { out.accept(SPEAKER.get()); out.accept(DECK.get()); out.accept(AMP.get()); out.accept(RACK.get()); out.accept(CASSETTE.get()); }).build()));
    private static Supplier<Block> equipment(String name, EquipmentBlock.Kind kind) {
        var properties = BlockBehaviour.Properties.of().setId(ResourceKey.create(Registries.BLOCK, id(name)))
            .mapColor(MapColor.WOOD).strength(1.5f).noOcclusion();
        Block block = Registry.register(BuiltInRegistries.BLOCK, id(name), new EquipmentBlock(kind, properties));
        Registry.register(BuiltInRegistries.ITEM, id(name), new EquipmentItem(block,
            new Item.Properties().setId(ResourceKey.create(Registries.ITEM, id(name)))));
        return fixed(block);
    }
    @Override public void onInitialize() {
        Network.init();
        ServerTickEvents.END_SERVER_TICK.register(server -> HiFiServer.get(server).tick());
        ServerLifecycleEvents.SERVER_STOPPED.register(HiFiServer::stop);
    }
}
