package com.imyvm.community.mixin;

import com.imyvm.community.application.townbuilding.BuildingRewardPreviewTracker;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockItem.class)
public class BlockItemPlaceMixin {

    @Inject(method = "place", at = @At("RETURN"))
    private void onPlace(BlockPlaceContext context, CallbackInfoReturnable<InteractionResult> cir) {
        if (!cir.getReturnValue().consumesAction()) return;
        if (!(context.getPlayer() instanceof ServerPlayer player)) return;

        BlockPos clickedPos = context.getClickedPos();
        BlockPos placedPos = resolvePlacedBlockTarget(
                clickedPos,
                context.getClickedFace(),
                context.getLevel().getBlockState(clickedPos),
                context.getLevel().getBlockState(clickedPos.relative(context.getClickedFace())),
                ((BlockItem) (Object) this).getBlock()
        );
        String objectId = BuiltInRegistries.BLOCK.getKey(((BlockItem) (Object) this).getBlock()).toString();
        BuildingRewardPreviewTracker.INSTANCE.recordPlacement(player, context.getLevel(), placedPos, objectId);
    }

    private static BlockPos resolvePlacedBlockTarget(
            BlockPos clickedPos,
            net.minecraft.core.Direction clickedFace,
            net.minecraft.world.level.block.state.BlockState clickedState,
            net.minecraft.world.level.block.state.BlockState adjacentState,
            net.minecraft.world.level.block.Block expectedBlock
    ) {
        BlockPos adjacentPos = clickedPos.relative(clickedFace);
        if (adjacentState.is(expectedBlock)) return adjacentPos;
        if (clickedState.is(expectedBlock)) return clickedPos;
        return adjacentPos;
    }
}
