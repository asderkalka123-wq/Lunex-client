package tech.onetap.util.player.combat;

import lombok.experimental.UtilityClass;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import tech.onetap.util.IMinecraft;
import tech.onetap.util.rotation.Rotation;

import java.util.Objects;
import java.util.function.Predicate;

@UtilityClass
public class RaytraceUtil implements IMinecraft {
    public BlockHitResult raycast(double range, Rotation angle, boolean includeFluids) {
        return raycast(Objects.requireNonNull(mc.player).getCameraPosVec(1.0F),range,angle,includeFluids);
    }

    public BlockHitResult raycast(Vec3d vec, double range, Rotation angle, boolean includeFluids) {
        Entity entity = mc.cameraEntity;

        if (entity == null) {
            return null;
        }

        Vec3d rotationVec = angle.toVector();
        Vec3d end = vec.add(rotationVec.x * range, rotationVec.y * range, rotationVec.z * range);

        World world = mc.world;
        if (world == null) {
            return null;
        }

        RaycastContext.FluidHandling fluidHandling = includeFluids ? RaycastContext.FluidHandling.ANY : RaycastContext.FluidHandling.NONE;
        RaycastContext context = new RaycastContext(vec, end, RaycastContext.ShapeType.OUTLINE, fluidHandling, entity);

        return world.raycast(context);
    }

    public BlockHitResult raycast(Vec3d start, Vec3d end, RaycastContext.ShapeType shapeType) {
        return raycast(start, end, shapeType, mc.player);
    }

    public BlockHitResult raycast(Vec3d start, Vec3d end, RaycastContext.ShapeType shapeType, Entity entity) {
        return mc.world.raycast(new RaycastContext(start, end, shapeType, RaycastContext.FluidHandling.NONE, entity));
    }

    public EntityHitResult raytraceEntity(double range, Rotation angle, Predicate<Entity> filter) {
        Entity entity = mc.cameraEntity;
        if (entity == null) return null;

        Vec3d cameraVec = entity.getCameraPosVec(1.0F);
        Vec3d rotationVec = angle.toVector();

        Vec3d vec3d3 = cameraVec.add(rotationVec.x * range, rotationVec.y * range, rotationVec.z * range);
        Box box = entity.getBoundingBox().stretch(rotationVec.multiply(range)).expand(1.0, 1.0, 1.0);

        return ProjectileUtil.raycast(entity, cameraVec, vec3d3, box, (e) -> !e.isSpectator() && filter.test(e), range * range);
    }

    public boolean rayTrace(Vec3d clientVec, double range, Box box) {
        Vec3d cameraVec = Objects.requireNonNull(mc.player).getEyePos();
        return box.contains(cameraVec) || box.raycast(cameraVec,cameraVec.add(clientVec.multiply(range))).isPresent();
    }

    public boolean hasFullBlockInPath(Vec3d start, Vec3d end) {
        World world = mc.world;
        if (world == null) return false;

        Vec3d dir = end.subtract(start);
        double totalDist = dir.length();
        if (totalDist < 0.001) return false;

        Vec3d step = dir.normalize().multiply(0.1);
        Vec3d current = start;
        double traveled = 0;

        while (traveled < totalDist) {
            BlockPos pos = BlockPos.ofFloored(current);
            BlockState state = world.getBlockState(pos);

            if (!state.isAir()) {
                Box blockBox = state.getCollisionShape(world, pos).getBoundingBox();
                Box worldBox = blockBox.offset(pos);

                if (worldBox.raycast(start, end).isPresent()) {
                    return true;
                }
            }

            current = current.add(step);
            traveled += 0.1;
        }
        return false;
    }

    public BlockHitResult raycastIgnoreNonFull(Vec3d start, Vec3d end, RaycastContext.ShapeType shapeType, Entity entity) {
        World world = mc.world;
        if (world == null) {
            return raycast(start, end, shapeType, entity);
        }

        BlockHitResult result = raycast(start, end, shapeType, entity);
        if (result.getType() == HitResult.Type.MISS) {
            return result;
        }

        Vec3d hitPos = result.getPos();
        BlockPos blockPos = result.getBlockPos();
        BlockState blockState = world.getBlockState(blockPos);

        if (!blockState.isAir() && isFullBlock(world, blockPos)) {
            return result;
        }

        BlockHitResult newResult = raycast(hitPos.add(0.001, 0.001, 0.001), end, shapeType, entity);
        return newResult;
    }

    private boolean isFullBlock(World world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        if (state.isAir()) return false;
        return state.getCollisionShape(world, pos).getBoundingBox().getAverageSideLength() >= 0.99;
    }
}