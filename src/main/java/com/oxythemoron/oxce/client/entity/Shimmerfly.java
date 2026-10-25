package com.oxythemoron.oxce.client.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.ambient.AmbientCreature;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.time.LocalDate;
import java.time.temporal.ChronoField;


public class Shimmerfly extends AmbientCreature {

    public static final float FLAP_LENGTH_SECONDS = 1.2F;
    public static final float TICKS_PER_FLAP = 10.0F;
    private static final EntityDataAccessor<Byte> DATA_ID_FLAGS;
    private static final int FLAG_RESTING = 1;
    private static final TargetingConditions BAT_RESTING_TARGETING;
    public final AnimationState flyAnimationState = new AnimationState();
    public final AnimationState restAnimationState = new AnimationState();
    public boolean hasRested = false;
    public boolean isAngry = false;
    @Nullable
    private BlockPos targetPosition;

    public Shimmerfly(EntityType<? extends Shimmerfly> entityType, Level level) {
        super(entityType, level);
        if (!level.isClientSide) {
            this.setResting(true);
        }

        this.moveControl = new FlyingMoveControl(this, 20, false);

    }

    public boolean isFlapping() {
        return !this.isResting() && (float)this.tickCount % 10.0F == 0.0F;
    }

    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_ID_FLAGS, (byte)0);
    }

    protected float getSoundVolume() {
        return 0.1F;
    }

    public float getVoicePitch() {
        return super.getVoicePitch() * 0.95F;
    }

    @Nullable
    public SoundEvent getAmbientSound() {
        return this.isResting() && this.random.nextInt(4) != 0 ? null : SoundEvents.BAT_AMBIENT;
    }

    protected SoundEvent getHurtSound(DamageSource damageSource) {
        return SoundEvents.BAT_HURT;
    }

    protected SoundEvent getDeathSound() {
        return SoundEvents.BAT_DEATH;
    }

    public boolean isPushable() {
        return false;
    }

    protected void doPush(Entity entity) {
    }

    protected void pushEntities() {
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, (double)6.0F);
    }

    public boolean isResting() {
        return ((Byte)this.entityData.get(DATA_ID_FLAGS) & 1) != 0;
    }

    public void setResting(boolean isResting) {
        byte b0 = (Byte)this.entityData.get(DATA_ID_FLAGS);
        if (isResting) {
            this.entityData.set(DATA_ID_FLAGS, (byte)(b0 | 1));
        } else {
            this.entityData.set(DATA_ID_FLAGS, (byte)(b0 & -2));
        }

    }

    public void tick() {
        super.tick();
        if (this.isResting()) {
            this.setDeltaMovement(Vec3.ZERO);
            this.setPosRaw(this.getX(), (double) Mth.floor(this.getY()) + (double)1.0F - (double)this.getBbHeight(), this.getZ());
        } else {
            this.setDeltaMovement(this.getDeltaMovement().multiply((double)1.0F, 0.6, (double)1.0F));
        }

        this.setupAnimationStates();
    }

    protected void customServerAiStep() {
        super.customServerAiStep();
        BlockPos blockpos = this.blockPosition();
        BlockPos[] nearbyBlockPositions = {
                blockpos.above(),
                blockpos.north(),
                blockpos.east(),
                blockpos.south(),
                blockpos.west(),
                blockpos.below()
        };


        //What it actually does when resting
        if (this.isResting()) {
            boolean flag = this.isSilent();
            //if block above. Run when it is resting. Stuff like player range detecting

            //Duplicated from down below, make much better
            BlockPos blockPos1 = null;
            for (int i = 0; i < nearbyBlockPositions.length; i++) {
                if (this.level().getBlockState(nearbyBlockPositions[i]).isRedstoneConductor(this.level(), nearbyBlockPositions[i])){
                    blockPos1 = nearbyBlockPositions[i];
                    break;
                }
            }

            if (blockPos1 != null && this.level().getBlockState(blockPos1).isRedstoneConductor(this.level(), blockpos)) {

                //Say I want it to look at player
                //

                if (this.level().getNearestPlayer(this, 22f) != null)
                {
                    Vec3 playerPos = this.level().getNearestPlayer(this, 22f).position();
                    Vec3 a1 = playerPos.subtract(this.position());
                    float f = (float)(Mth.atan2(a1.z, a1.x) * (double)180.0F / (double)(float)Math.PI) - 90.0F;
                    this.yHeadRot = f + 180.0f;
                }
                else if (this.random.nextInt(600) == 0){
                    this.yHeadRot = (float)this.random.nextInt(360);
                }


                //Reposition randomly (should make it so it doesn't land on the same wall immediately)
                if (this.random.nextInt(1000) == 0) {
                    this.setResting(false);

                }

                //If passes the predicate (4 blocks distance), set resting to false so it flies off when you get close
                if (this.level().getNearestPlayer(BAT_RESTING_TARGETING, this) != null) {
                    this.setResting(false);
                    if (!flag) {
                        this.level().levelEvent((Player)null, 1025, blockpos, 0);
                    }
                }

            } else {
                this.setResting(false);
                if (!flag) {
                    this.level().levelEvent((Player)null, 1025, blockpos, 0);
                }
            }
        } else {
            /*
            Movement code?

            if (this.targetPosition != null && (!this.level().isEmptyBlock(this.targetPosition) || this.targetPosition.getY() <= this.level().getMinBuildHeight())) {
                this.targetPosition = null;
            }

            if (this.targetPosition == null || this.random.nextInt(30) == 0 || this.targetPosition.closerToCenterThan(this.position(), (double)2.0F)) {
                this.targetPosition = BlockPos.containing(this.getX() + (double)this.random.nextInt(7) - (double)this.random.nextInt(7), this.getY() + (double)this.random.nextInt(6) - (double)2.0F, this.getZ() + (double)this.random.nextInt(7) - (double)this.random.nextInt(7));
            }

            double d2 = (double)this.targetPosition.getX() + (double)0.5F - this.getX();
            double d0 = (double)this.targetPosition.getY() + 0.1 - this.getY();
            double d1 = (double)this.targetPosition.getZ() + (double)0.5F - this.getZ();
            Vec3 vec3 = this.getDeltaMovement();

            Vec3 vec31 = vec3.add((Math.signum(d2) * (double)0.5F - vec3.x) * (double)0.1F, (Math.signum(d0) * (double)0.7F - vec3.y) * (double)0.1F, (Math.signum(d1) * (double)0.5F - vec3.z) * (double)0.1F);
            this.setDeltaMovement(vec31);
            float f = (float)(Mth.atan2(vec31.z, vec31.x) * (double)180.0F / (double)(float)Math.PI) - 90.0F;
            float f1 = Mth.wrapDegrees(f - this.getYRot());
            this.zza = 0.5F;
            this.setYRot(this.getYRot() + f1);
            if (this.random.nextInt(10) == 0 && this.level().getBlockState(blockpos1).isRedstoneConductor(this.level(), blockpos1)) {
                this.setResting(true);
            }
            */

            if (this.targetPosition != null && (!this.level().isEmptyBlock(this.targetPosition) || this.targetPosition.getY() <= this.level().getMinBuildHeight())) {
                this.targetPosition = null;
            }

            //Change random int to change chances of changing target
            //Selects a random target to move to. Interp later on. Then changes vector to move towards until at the point.
            if (this.targetPosition == null || this.random.nextInt(200) == 0 || this.targetPosition.closerToCenterThan(this.position(), (double)2.0F)) {
                this.targetPosition = BlockPos.containing(this.getX() + (double)this.random.nextInt(7) - (double)this.random.nextInt(7), this.getY() + (double)this.random.nextInt(6) - (double)2.0F, this.getZ() + (double)this.random.nextInt(7) - (double)this.random.nextInt(7));
            }

            //Distances from current spot to target
            double d2 = (double)this.targetPosition.getX() + (double)0.5F - this.getX();
            double d0 = (double)this.targetPosition.getY() + 0.1 - this.getY();
            double d1 = (double)this.targetPosition.getZ() + (double)0.5F - this.getZ();
            Vec3 vec3 = this.getDeltaMovement();
            //New vector. Movement per frame. XYZ
            //Signum checks if input is pos or neg, exactly 1 if pos, -1 if neg and 0 if 0
            //0.5 limits max to +0.5 -0.5
            //Subtract current velocity to find difference between desired motion and current.
            //Multiplies speed of rate of change by 0.1 for smooth movement
            //Adds these values onto the current movement, as a smooth operation or lerp
            Vec3 vec31 = vec3.add((Math.signum(d2) * (double)0.5F - vec3.x) * (double)0.1F, (Math.signum(d0) * (double)0.7F - vec3.y) * (double)0.1F, (Math.signum(d1) * (double)0.5F - vec3.z) * (double)0.1F);
            this.setDeltaMovement(vec31);
            //f1 is rotation vector for Y. Set it to 1, turns slowly because it sets new Y rot to current plus f. Set to 10, turns slightly faster. Doesn't seem to change movement direction
            //So f is a sort of target using the current movement vector
            //Look top down. Axes are straight so you have opposite and adjacent. Atan thereforce gives radian angle
            //The 180/PI converts to degrees (I REMEMBER THIS) then adds some offset. So this sets the target rotation to this value
            //When top down, z is up, x is right
            float f = (float)(Mth.atan2(vec31.z, vec31.x) * (double)180.0F / (double)(float)Math.PI) - 90.0F;
            //If f is a target degrees, f1 becomes the change in rotation to reach the target
            float f1 = Mth.wrapDegrees(f - this.getYRot());
            this.zza = 0.5F;
            //Adds the rotation change needed to current so it faces the right direction
            //This means that rotations use degrees around the y axes. Simple. Change f1 by 0.01 and it turns slower
            this.setYRot(this.getYRot() + (f1));
            //How it decides if it should rest. Blockpos1 is above, redstone conduct means full block. Sets resting true here.

            //How it reads if it can land on something
            for (int i = 0; i < nearbyBlockPositions.length; i++) {
                if (this.random.nextInt(10) == 0 && this.level().getBlockState(nearbyBlockPositions[i]).isRedstoneConductor(this.level(), nearbyBlockPositions[i])){
                    this.setResting(true);
                    break;
                }
            }

        }

    }


    protected Entity.MovementEmission getMovementEmission() {
        return MovementEmission.EVENTS;
    }

    protected void checkFallDamage(double y, boolean onGround, BlockState state, BlockPos pos) {
    }

    public boolean isIgnoringBlockTriggers() {
        return true;
    }

    public boolean hurt(DamageSource source, float amount) {
        if (this.isInvulnerableTo(source)) {
            return false;
        } else {
            if (!this.level().isClientSide && this.isResting()) {
                this.setResting(false);
            }

            return super.hurt(source, amount);
        }
    }

    //No idea
    public void readAdditionalSaveData(CompoundTag compound) {
        super.readAdditionalSaveData(compound);
        this.entityData.set(DATA_ID_FLAGS, compound.getByte("ShimmerflyFlags"));
    }

    public void addAdditionalSaveData(CompoundTag compound) {
        super.addAdditionalSaveData(compound);
        compound.putByte("BatFlags", (Byte)this.entityData.get(DATA_ID_FLAGS));
    }

    public static boolean checkBatSpawnRules(EntityType<Shimmerfly> shimmerfly, LevelAccessor level, MobSpawnType spawnType, BlockPos pos, RandomSource random) {
        if (pos.getY() >= level.getSeaLevel()) {
            return false;
        } else {
            int i = level.getMaxLocalRawBrightness(pos);
            int j = 4;
            if (isHalloween()) {
                j = 7;
            } else if (random.nextBoolean()) {
                return false;
            }

            return i > random.nextInt(j) ? false : checkMobSpawnRules(shimmerfly, level, spawnType, pos, random);
        }
    }

    private static boolean isHalloween() {
        LocalDate localdate = LocalDate.now();
        int i = localdate.get(ChronoField.DAY_OF_MONTH);
        int j = localdate.get(ChronoField.MONTH_OF_YEAR);
        return j == 10 && i >= 20 || j == 11 && i <= 3;
    }

    private void setupAnimationStates() {
        if (this.isResting()) {
            this.flyAnimationState.stop();
            this.restAnimationState.startIfStopped(this.tickCount);
        } else {
            this.restAnimationState.stop();
            this.flyAnimationState.startIfStopped(this.tickCount);
        }

    }

    //Unsure
    static {
        DATA_ID_FLAGS = SynchedEntityData.defineId(Shimmerfly.class, EntityDataSerializers.BYTE);
        //A predicate used to find the nearest player. Within range of 4 blocks.
        BAT_RESTING_TARGETING = TargetingConditions.forNonCombat().range((double)8.0F);
    }


}

