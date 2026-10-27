package com.oxythemoron.oxce.client.entity;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.MoveToBlockGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomFlyingGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.FlyingAnimal;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.PowderSnowBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.time.LocalDate;
import java.time.temporal.ChronoField;
import java.util.EnumSet;
import java.util.function.Predicate;

//For any part of the original bat movement and resting code, just look at the bat as this was a direct copy
public class Shimmerfly extends Animal implements FlyingAnimal {
    public static final Logger LOGGER = LogUtils.getLogger();
    public static final float FLAP_LENGTH_SECONDS = 1.2F;
    public static final float TICKS_PER_FLAP = 10.0F;
    private static final EntityDataAccessor<Byte> DATA_ID_FLAGS;
    private static final int FLAG_RESTING = 1;
    public static final TargetingConditions BAT_RESTING_TARGETING;
    public final AnimationState flyAnimationState = new AnimationState();
    public final AnimationState restAnimationState = new AnimationState();
    private float restCooldown = 20;

    @Nullable
    private BlockPos targetPosition;

    public Shimmerfly(EntityType<? extends Shimmerfly> entityType, Level level) {
        super(entityType, level);
        if (!level.isClientSide) {
            this.setResting(false);
        }

        this.moveControl = new FlyingMoveControl(this, 2, false);
        this.setPathfindingMalus(PathType.DANGER_FIRE, -1.0F);

    }

    //Iterates through these every tick, from 0 upwards.
    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new ShimmerflyDestroyTorchGoal(this, (double)1.0F));
        this.goalSelector.addGoal(0, new ShimmerflyAvoidHostileGoal(this, Player.class, 10.0f, 1.2f, 1.0f));
        this.goalSelector.addGoal(3, new ShimmerflyRandomFlying(this, (double)1.0f));
        this.goalSelector.addGoal(2, new ShimmerflyRestOnSurfaceGoal(this));
    }

    public boolean isFlapping() {
        return !this.isResting() && (float)this.tickCount % 10.0F == 0.0F;
    }

    @Override
    public @org.jetbrains.annotations.Nullable AgeableMob getBreedOffspring(ServerLevel serverLevel, AgeableMob ageableMob) {
        return null;
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

    //Was missing flying speed here, broke flying pathfinding. See what attributes are available and what i'd need
    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, (double)6.0F)
                .add(Attributes.FLYING_SPEED, 0.2f)
                .add(Attributes.MOVEMENT_SPEED, (double)1.0F);
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
        if (this.restCooldown > 0){
            this.restCooldown--;
        }


        //Stops movement if resting so it can change animations which seem tied to speed?
        //needed because it constantly moves unless it's hit a block and is resting
        if (this.isResting()) {
          this.setDeltaMovement(Vec3.ZERO);
          this.setPosRaw(this.getX(), (double) Mth.floor(this.getY()) + (double)1.0F - (double)this.getBbHeight(), this.getZ());
        } else {
          //this.setDeltaMovement(this.getDeltaMovement().multiply(1.0F, 1.0f, (double)1.0F));
        }

        this.setupAnimationStates();
    }



    //Funky behaviour seen and this fixes it. Starts to travel and uses the right movement speed, but once in air it clamps speed to something really slow.
    //Animal travel method will clamp movement to a low value to mimic strafing speed when falling. So it uses a slow clamped speed after the first frame
    //Look at other travel method in living entity, but it seems designed to be overriden. See how the original works to understand it.
    //Cause the existing one does a flyingEntity check at the end to fix animations, it proves that this travel method isn't meant for flying things
    //Looking into it, moveControl determines an impulse to get to a spot, and pathfinding creates a list of places that reaches a point. travel applies physics limitations then does move to actually move the thing. This is where flying speed is used. That's why it kept resetting the speed after one frame.
    @Override
    public void travel(Vec3 travelVector) {
        if (this.isControlledByLocalInstance()) {
            if (this.isInFluidType()) {
                super.travel(travelVector);
                return;
            } else if (this.isResting()){
                super.travel(travelVector);
                return;
            }
            float flyingSpeed = (float) this.getAttributeValue(Attributes.FLYING_SPEED);

            // 3. Inject our 3D movement inputs relative to the current looking direction
            this.moveRelative(flyingSpeed, travelVector);

            // 4. Execute the actual movement in the world physics grid
            this.move(MoverType.SELF, this.getDeltaMovement());

            // 5. Apply fluid flying friction (0.91F) so it naturally slows down when AI inputs stop
            float friction = this.onGround() ? this.level().getBlockState(this.getBlockPosBelowThatAffectsMyMovement()).getBlock().getFriction() * 0.91F : 0.91F;
            this.setDeltaMovement(this.getDeltaMovement().scale(friction));
        }

        // Process flight animations cleanly
        this.calculateEntityAnimation(true);
    }



    protected void customServerAiStep() {
        super.customServerAiStep();

            /*
            Movement code?
            Did a complete overhaul. Old bats moved very randomly and only rested if a block was above them.
            This did teach me how it's movement worked and how rotation worked, which was useful

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

            //Signum is 1 if the output is at all positive, 0 if 0 and -1 if at all negative
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



            /* COMMENTED TO USE GOAL AND PATHFINDING
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
            */
            //How it reads if it can land on something

        }



    //For making the navigation brain. Defaults to ground navigation, so override for flying
    @Override
    protected PathNavigation createNavigation(Level p_level) {
        FlyingPathNavigation flyingpathnavigation = new FlyingPathNavigation(this, p_level) {
            @Override
            public boolean isStableDestination(BlockPos p_27947_) {
                return this.level.getBlockState(p_27947_).isAir();
            }

            @Override
            public void tick() {
                super.tick();

            }
        };
        flyingpathnavigation.setCanOpenDoors(false);
        flyingpathnavigation.setCanFloat(false);
        flyingpathnavigation.setCanPassDoors(true);
        return flyingpathnavigation;
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

    @Override
    public boolean isFood(ItemStack itemStack) {
        return false;
    }

    public void addAdditionalSaveData(CompoundTag compound) {
        super.addAdditionalSaveData(compound);
        compound.putByte("ShimmerflyFlags", (Byte)this.entityData.get(DATA_ID_FLAGS));
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

    @Override
    public boolean isFlying() {
        return true;
    }

    private class ShimmerflyRestOnSurfaceGoal extends Goal {
        //Give each goal a reference to the entity itself because it doesnt have one natively
        private final Shimmerfly shimmerfly;
        private int restTimer;


        public ShimmerflyRestOnSurfaceGoal(Shimmerfly mob){
            this.shimmerfly = mob;
            //Multiple goals can run if they aren't in the same flag. move, look, target and jump. Give something look and move and a separate move can't happen at once
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        //can use this goal if it near a block and runs the random number properly
        @Override
        public boolean canUse() {

            if (this.shimmerfly.restCooldown > 0) {
                return false;
            }
            if (this.shimmerfly.getRandom().nextInt(reducedTickDelay(10)) == 0) {
                return false;
            }
            if (this.shimmerfly.level().getNearestPlayer(BAT_RESTING_TARGETING, this.shimmerfly) != null) {
                return false;
            }

            BlockPos blockpos = this.shimmerfly.blockPosition();
            BlockPos[] nearbyBlockPositions = {
                    blockpos.above(),
                    blockpos.north(),
                    blockpos.east(),
                    blockpos.south(),
                    blockpos.west(),
                    blockpos.below()
            };
            for (int i = 0; i < nearbyBlockPositions.length; i++) {
                if (this.shimmerfly.level().getBlockState(nearbyBlockPositions[i]).isRedstoneConductor(this.shimmerfly.level(), nearbyBlockPositions[i])){
                    return true;

                }
            }
            return false;
        }

        @Override
        public void start() {
            this.shimmerfly.setResting(true);
            this.restTimer = reducedTickDelay(200 + this.shimmerfly.getRandom().nextInt(400));
            super.start();
        }

        @Override
        public void tick() {
            this.restTimer--;
            //Say I want it to look at player
            //

            Player player = this.shimmerfly.level().getNearestPlayer(this.shimmerfly, 22.0);
            if (player != null) {
                //this.shimmerfly.getLookControl().setLookAt(player);

                //Uneeded, above uses the look control thing directly

                Vec3 playerPos = this.shimmerfly.level().getNearestPlayer(this.shimmerfly, 22f).position();
                Vec3 a1 = playerPos.subtract(this.shimmerfly.position());
                float f = (float)(Mth.atan2(a1.z, a1.x) * (double)180.0F / (double)(float)Math.PI) - 90.0F;
                this.shimmerfly.yHeadRot = f + 180.0f;


            }
            else if (this.shimmerfly.random.nextInt(600) == 0){
                this.shimmerfly.yHeadRot = (float)this.shimmerfly.random.nextInt(360);
            }



            //If passes the predicate (4 blocks distance), set resting to false so it flies off when you get close
            //if (this.shimmerfly.level().getNearestPlayer(this.shimmerfly.BAT_RESTING_TARGETING, this.shimmerfly) != null) {
                //this.shimmerfly.setResting(false);
                //if (!shimmerfly.flag) {
                   // this.shimmerfly.level().levelEvent((Player)null, 1025, shimmerfly.blockpos, 0);
                //}
            //}

            super.tick();
        }

        //When resting, set a timer for how long it should rest for and lure things
        @Override
        public boolean canContinueToUse() {
            return this.restTimer > 0
                    && this.shimmerfly.isResting();
        }

        protected int nextStartTick(PathfinderMob creature) {
            return reducedTickDelay(20 + creature.getRandom().nextInt(80));
        }

        @Override
        public void stop() {
            this.shimmerfly.setResting(false);
            this.shimmerfly.restCooldown = nextStartTick(this.shimmerfly);
            super.stop();
        }
    }

    //Example of how easy it is to add existing functionality. Looking at the goal class shows what it does.
    //Don't need to know how pathfinding works fully but I can trace back to randomStrollGoal. It uses moveControl on the mob which needs to be set in the constructor.
    //Look at other entities to see what things they define in their constructor and other goals, no need to remember it as it's all written already
    private class ShimmerflyRandomFlying extends WaterAvoidingRandomFlyingGoal {
        private final Shimmerfly shimmerfly;

        public ShimmerflyRandomFlying(Shimmerfly mob, double speedModifier) {
            super(mob, speedModifier);
            this.shimmerfly = mob;
            this.setInterval(4);
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return (this.shimmerfly.getRandom().nextInt(1) == 0) && super.canUse();

        }

        @Override
        public void start() {
            this.shimmerfly.setResting(false);
            super.start();
        }

        @Override
        public boolean canContinueToUse() {
            return super.canContinueToUse();
        }
    }

    //Super easy to implement existing functionality. Extend the existing goal or just add it to the registerGoals list above
    //Look at hierarchy for Goal class to see what others exist, can directly use them if I want or extend them if they do what I want already
    //See what the other classes do, what variables they have and use, how to override that themselves. No need to remember it all, just have a search and I'll get it working like I did the goals here
    private class ShimmerflyDestroyTorchGoal extends MoveToBlockGoal {
        private static final int GIVE_UP_TICKS = 2000;
        private final Shimmerfly shimmerfly;


        public ShimmerflyDestroyTorchGoal(Shimmerfly mob, double speedModifier) {
            super(mob, speedModifier, 24, 24);
            this.shimmerfly = mob;
            this.nextStartTick = 100;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        //Once in the state, what needs to be true to carry on. Once false it does something else
        @Override
        public boolean canContinueToUse() {
            return this.tryTicks <= GIVE_UP_TICKS && this.isValidTarget(this.shimmerfly.level(), this.blockPos);
        }

        //Fired when it wants to do the action but needs to check to see if any other attributes should prevent it
        //If a priority 1 goal is currently running and canContinueTouse is still true, if the level 0 goal says it can use it, it will activate that and stop the level 1
        @Override
        public boolean canUse() {
            return super.canUse();
        }

        //From moveToBlockGoal. To check what the target is. No need to do it myself
        @Override
        protected boolean isValidTarget(LevelReader level, BlockPos blockPos) {
            return level.getBlockState(blockPos).is(Blocks.TORCH);
        }

        //Override what the inherited class does using it's handy virtual methods to change functionality. A lot of it is there already, just need to change it.
        //Some methods will be hidden, need to find them by looking in the class. First check if something already does it to get a good start.
        @Override
        protected void moveMobToBlock() {
            //Essentially what the base method uses, but set to the block below by taking away +1 from getY
            this.mob.getNavigation().moveTo((double)this.blockPos.getX() + (double)0.5F, (double)(this.blockPos.getY()), (double)this.blockPos.getZ() + (double)0.5F, this.speedModifier);
        }

        public boolean shouldRecalculatePath() {
            return this.tryTicks % 40 == 0;
        }

        //In base moveToBlock is sets a 200 delay with +200 as a random value, this is a long time. Can override it here
        //Next start tick counts down, once below zero it finds nearest block. Look in MoveToBlockGoal
        @Override
        protected int nextStartTick(PathfinderMob creature) {
            return reducedTickDelay(80 + creature.getRandom().nextInt(20));
        }

        //Base accepted distance is 1 block, too far since I need to be in the block
        //thankfully it's a method in the goal so I can just override it
        @Override
        public double acceptedDistance() {
            return (double) 0.5f;
        }

        //Be logical with what is in start or stop. If it will always stop resting when entering, set it there. Think about it
        @Override
        public void start() {
            this.shimmerfly.setResting(false);
            super.start();

        }

        //Checks if the current block is a torch, break if so
        @Override
        public void tick() {
            super.tick();
            BlockPos blockpos = this.shimmerfly.blockPosition();
            if (this.shimmerfly.level().getBlockState(blockpos).is(Blocks.TORCH)){
                this.shimmerfly.level().destroyBlock(blockpos, true);
            }
        }
    }

    private class ShimmerflyAvoidHostileGoal<T extends LivingEntity> extends AvoidEntityGoal<T> {
        private final Shimmerfly shimmerfly;

        @Override
        public boolean canUse() {
            return super.canUse();
        }

        @Override
        public void start() {
            this.shimmerfly.setResting(false);
            super.start();
        }

        @Override
        public void tick() {
            super.tick();
        }

        public ShimmerflyAvoidHostileGoal(Shimmerfly mob, Class<T> entityClassToAvoid, float maxDistance, double walkSpeedModifier, double sprintSpeedModifier) {
            super(mob, entityClassToAvoid, maxDistance, walkSpeedModifier, sprintSpeedModifier);
            this.shimmerfly = mob;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }
    }
}



