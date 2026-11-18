package de.codemorra.sheeper.entity.navigation;

import de.codemorra.sheeper.entity.WaterSheeperEntity;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;

/**
 * Converts three-dimensional water waypoints into body-relative swimming input.
 * Unlike vanilla's swim controller, this explicitly recognizes custom boss
 * water
 * and keeps head tracking independent of the body's navigation direction.
 */
public final class WaterSwimMoveControl extends MoveControl {
    private final WaterSheeperEntity boss;

    /** @param boss owner whose water navigation supplies the current waypoint */
    public WaterSwimMoveControl(WaterSheeperEntity boss) {
        super(boss);
        this.boss = boss;
    }

    @Override
    public void tick() {
        if (!boss.isInArenaWater()) {
            super.tick();
            return;
        }
        if (operation != Operation.MOVE_TO || boss.getNavigation().isDone()) {
            stopInputs();
            return;
        }
        double dx = wantedX - boss.getX();
        double dy = wantedY - boss.getY();
        double dz = wantedZ - boss.getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        double distance = Math.sqrt(horizontal * horizontal + dy * dy);
        if (distance < 1.0E-4D) {
            stopInputs();
            return;
        }
        if (horizontal > 1.0E-4D) {
            float yaw = (float) (Mth.atan2(dz, dx) * 180.0D / Math.PI) - 90.0F;
            boss.setYRot(rotlerp(boss.getYRot(), yaw, 20.0F));
            boss.yBodyRot = boss.getYRot();
        }
        float speed = (float) (speedModifier * boss.getAttributeValue(Attributes.MOVEMENT_SPEED));
        boss.setSpeed(speed);
        // Steer toward the waypoint even while the body is still turning. Pure
        // forward thrust otherwise traces circles around close water nodes.
        float yawRadians = boss.getYRot() * Mth.DEG_TO_RAD;
        double sin = Mth.sin(yawRadians);
        double cos = Mth.cos(yawRadians);
        double approach = Math.min(1.0D, distance / 0.5D);
        if (distance < 0.5D) {
            // Brake water momentum near a turn instead of overshooting its node.
            boss.setDeltaMovement(boss.getDeltaMovement().scale(0.5D));
        }
        double input = speed * approach / distance;
        boss.setXxa((float) ((dx * cos + dz * sin) * input));
        boss.setZza((float) ((dz * cos - dx * sin) * input));
        boss.setYya((float) (dy * input));
    }

    /**
     * Neutral input allows water drag to slow the boss without steering it toward
     * the floor.
     */
    private void stopInputs() {
        boss.setSpeed(0);
        boss.setXxa(0);
        boss.setYya(0);
        boss.setZza(0);
    }
}
