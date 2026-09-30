package ru.daniel.soviethifi;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.*;
import ru.daniel.soviethifi.block.*;

public class EquipmentGameTests {
    private static final String HASH = "a".repeat(64);
    private static final BlockPos POS = new BlockPos(1, 1, 1);
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private Player player(GameTestHelper h) {
        Player player = h.makeMockPlayer(GameType.SURVIVAL);
        var p = h.absolutePos(new BlockPos(4,1,4));
        player.setPos(p.getX(), p.getY(), p.getZ());
        return player;
    }
    @GameTest public void recordOneFromBlankStack(GameTestHelper h) {
        var player = player(h);
        var stack = new ItemStack(SovietHiFi.CASSETTE.get(), 64);
        require(stack.getMaxStackSize() == 64, "Blank cassettes must stack to 64");
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        require(CassetteItem.finishRecording(player, InteractionHand.MAIN_HAND, stack, HASH, "Test"), "Recording failed");
        require(stack.getCount() == 63 && CassetteItem.track(stack).isEmpty(), "Original blanks were modified");
        int recorded = 0;
        for (int i=0; i<player.getInventory().getContainerSize(); i++) {
            var item = player.getInventory().getItem(i);
            if (CassetteItem.track(item).equals(HASH)) { recorded += item.getCount(); require(item.getMaxStackSize()==1, "Recorded tape is stackable"); }
        }
        require(recorded == 1, "Expected one recorded cassette");
        h.succeed();
    }
    @GameTest public void recordSingleInOffhand(GameTestHelper h) {
        var player = player(h); var stack = new ItemStack(SovietHiFi.CASSETTE.get());
        player.setItemInHand(InteractionHand.OFF_HAND, stack);
        require(CassetteItem.finishRecording(player, InteractionHand.OFF_HAND, stack, HASH, "Test"), "Offhand recording failed");
        var result=player.getOffhandItem();
        require(result.getCount()==1 && result.getMaxStackSize()==1 && CassetteItem.track(result).equals(HASH), "Offhand cassette lost");
        h.succeed();
    }
    @GameTest public void movedCassetteCancelsRecording(GameTestHelper h) {
        var player = player(h); var original = new ItemStack(SovietHiFi.CASSETTE.get(), 8);
        player.setItemInHand(InteractionHand.MAIN_HAND, original.copy());
        require(!CassetteItem.finishRecording(player, InteractionHand.MAIN_HAND, original, HASH, "Test"), "Changed hand was accepted");
        require(original.getCount()==8 && CassetteItem.track(original).isEmpty(), "Cancelled recording consumed a blank");
        h.succeed();
    }
    @GameTest public void fullInventoryDropsOneRecordedCassette(GameTestHelper h) {
        var player = player(h);
        for(int i=0;i<36;i++)player.getInventory().setItem(i,new ItemStack(Items.STONE,64));
        var stack=new ItemStack(SovietHiFi.CASSETTE.get(),8);
        player.setItemInHand(InteractionHand.MAIN_HAND,stack);
        require(CassetteItem.finishRecording(player,InteractionHand.MAIN_HAND,stack,HASH,"Test"),"Full inventory failed");
        require(stack.getCount()==7 && CassetteItem.track(stack).isEmpty(),"Blank count changed incorrectly");
        int count=h.getLevel().getEntitiesOfClass(ItemEntity.class,player.getBoundingBox().inflate(2)).stream()
            .filter(e->CassetteItem.track(e.getItem()).equals(HASH)).mapToInt(e->e.getItem().getCount()).sum();
        require(count==1,"Recorded cassette must drop exactly once when inventory is full");
        h.succeed();
    }
    private EquipmentEntity placePair(GameTestHelper h, boolean ampOnTop) {
        h.setBlock(POS.below(), Blocks.OAK_PLANKS);
        h.setBlock(POS, ampOnTop ? SovietHiFi.DECK.get() : SovietHiFi.AMP.get());
        var before=h.getBlockEntity(POS,EquipmentEntity.class);
        before.volume=.25f; before.tape=.1f;
        if(ampOnTop) {
            var cassette=new ItemStack(SovietHiFi.CASSETTE.get()); CassetteItem.record(cassette,HASH,"Kept tape");
            before.tapes.add(cassette); before.pausedTicks=123;
        }
        var player=player(h);
        var held=new ItemStack(ampOnTop ? SovietHiFi.AMP.get() : SovietHiFi.DECK.get(), 2);
        player.setItemInHand(InteractionHand.MAIN_HAND,held);
        var pos=h.absolutePos(POS);
        var hit=new BlockHitResult(new Vec3(pos.getX()+.5,pos.getY()+(ampOnTop?.5:.3125),pos.getZ()+.5),Direction.UP,pos,false);
        // Exercise the actual block interaction and fallback-to-item path of ordinary right-click.
        h.useBlock(POS,player,hit);
        require(held.getCount()==1,"Placement must consume exactly one device");
        h.assertBlockPresent(SovietHiFi.STACK.get(),POS);
        var after=h.getBlockEntity(POS,EquipmentEntity.class);
        require(after.getBlockState().getValue(StackedEquipmentBlock.AMP_ON_TOP)==ampOnTop,"Wrong device order");
        require(after.supports(EquipmentBlock.Kind.AMP)&&after.supports(EquipmentBlock.Kind.DECK),"Pair lost functionality");
        require(after.volume==.25f&&after.tape==.1f,"Settings lost while combining");
        if(ampOnTop) require(after.tapes.size()==1&&after.trackId().equals(HASH)&&after.pausedTicks==123,"Cassette/playback position lost");
        require(h.getLevel().getEntitiesOfClass(ItemEntity.class,new AABB(pos).inflate(1)).isEmpty(),"Combining unexpectedly dropped inventory");
        return after;
    }
    @GameTest public void amplifierOnLoadedDeckAndSaveRoundTrip(GameTestHelper h) {
        var pair=placePair(h,true);
        var restored=(EquipmentEntity)BlockEntity.loadStatic(pair.getBlockPos(),pair.getBlockState(),
            pair.saveWithFullMetadata(h.getLevel().registryAccess()),h.getLevel().registryAccess());
        require(restored!=null&&restored.trackId().equals(HASH)&&restored.volume==.25f&&restored.pausedTicks==123,"Save round trip lost pair data");
        h.succeed();
    }
    @GameTest public void deckOnAmplifier(GameTestHelper h) { placePair(h,false); h.succeed(); }
    @GameTest public void breakingPairDropsBothDevicesAndCassetteOnce(GameTestHelper h) {
        var pair=placePair(h,true); var pos=pair.getBlockPos();
        h.getLevel().destroyBlock(pos,true);
        var drops=h.getLevel().getEntitiesOfClass(ItemEntity.class,new AABB(pos).inflate(1));
        require(drops.stream().filter(e->e.getItem().is(SovietHiFi.DECK.get().asItem())).mapToInt(e->e.getItem().getCount()).sum()==1,"Deck loot wrong");
        require(drops.stream().filter(e->e.getItem().is(SovietHiFi.AMP.get().asItem())).mapToInt(e->e.getItem().getCount()).sum()==1,"Amp loot wrong");
        require(drops.stream().filter(e->CassetteItem.track(e.getItem()).equals(HASH)).mapToInt(e->e.getItem().getCount()).sum()==1,"Tape loot wrong");
        h.succeed();
    }
}
