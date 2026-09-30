package ru.daniel.soviethifi.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import ru.daniel.soviethifi.SovietHiFi;

public final class StackedEquipmentBlock extends EquipmentBlock {
    public static final BooleanProperty AMP_ON_TOP = BooleanProperty.create("amp_on_top");
    public StackedEquipmentBlock(Properties properties) { super(Kind.STACK, properties); }
    @Override protected com.mojang.serialization.MapCodec<? extends net.minecraft.world.level.block.BaseEntityBlock> codec() {
        return simpleCodec(StackedEquipmentBlock::new);
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(AMP_ON_TOP);
    }
    @Override protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData) {
        return new ItemStack(state.getValue(AMP_ON_TOP) ? SovietHiFi.AMP.get() : SovietHiFi.DECK.get());
    }
}
