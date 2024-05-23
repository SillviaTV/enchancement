/*
 * All Rights Reserved (c) MoriyaShiine
 */

package moriyashiine.enchancement.common.component.entity;

import moriyashiine.enchancement.client.EnchancementClient;
import moriyashiine.enchancement.common.init.ModEnchantments;
import moriyashiine.enchancement.common.init.ModSoundEvents;
import moriyashiine.enchancement.common.payload.SlideResetVelocityPayload;
import moriyashiine.enchancement.common.payload.SlideSetVelocityPayload;
import moriyashiine.enchancement.common.payload.SlideSlamPayload;
import moriyashiine.enchancement.common.util.EnchancementUtil;
import moriyashiine.enchancement.mixin.util.accessor.EntityAccessor;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.GameOptions;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.math.*;
import net.minecraft.world.event.GameEvent;
import org.ladysnake.cca.api.v3.component.tick.CommonTickingComponent;

import java.util.UUID;

public class SlideComponent implements CommonTickingComponent {
	public static final int DEFAULT_JUMP_BOOST_RESET_TICKS = 5, DEFAULT_SLAM_COOLDOWN = 7;

	private static final EntityAttributeModifier STEP_HEIGHT_INCREASE = new EntityAttributeModifier(UUID.fromString("f95ce6ed-ecf3-433b-a7f0-a9c6092b0cf7"), "Enchantment modifier", 1, EntityAttributeModifier.Operation.ADD_VALUE);

	private final PlayerEntity obj;
	private Vec3d velocity = Vec3d.ZERO;
	private boolean shouldSlam = false;
	private int jumpBoostResetTicks = DEFAULT_JUMP_BOOST_RESET_TICKS, slamCooldown = DEFAULT_SLAM_COOLDOWN, ticksLeftToJump = 0, ticksSliding = 0;

	private int slideLevel = 0;
	private boolean hasSlide = false;

	private boolean disallowSlide = false, wasPressingSlamKey = false;

	public SlideComponent(PlayerEntity obj) {
		this.obj = obj;
	}

	@Override
	public void readFromNbt(NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
		velocity = new Vec3d(tag.getDouble("VelocityX"), tag.getDouble("VelocityY"), tag.getDouble("VelocityZ"));
		shouldSlam = tag.getBoolean("ShouldSlam");
		jumpBoostResetTicks = tag.getInt("JumpBoostResetTicks");
		slamCooldown = tag.getInt("SlamCooldown");
		ticksLeftToJump = tag.getInt("TicksLeftToJump");
		ticksSliding = tag.getInt("TicksSliding");
	}

	@Override
	public void writeToNbt(NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
		tag.putDouble("VelocityX", velocity.getX());
		tag.putDouble("VelocityY", velocity.getY());
		tag.putDouble("VelocityZ", velocity.getZ());
		tag.putBoolean("ShouldSlam", shouldSlam);
		tag.putInt("JumpBoostResetTicks", jumpBoostResetTicks);
		tag.putInt("SlamCooldown", slamCooldown);
		tag.putInt("TicksLeftToJump", ticksLeftToJump);
		tag.putInt("TicksSliding", ticksSliding);
	}

	@Override
	public void tick() {
		slideLevel = EnchantmentHelper.getEquipmentLevel(ModEnchantments.SLIDE, obj);
		hasSlide = slideLevel > 0;
		if (hasSlide) {
			if (obj.isSneaking() || obj.isTouchingWater()) {
				velocity = Vec3d.ZERO;
			}
			if (slamCooldown > 0) {
				slamCooldown--;
			}
			if (ticksLeftToJump > 0) {
				ticksLeftToJump--;
			}
			if (isSliding()) {
				((EntityAccessor) obj).enchancement$spawnSprintingParticles();
				obj.getWorld().emitGameEvent(GameEvent.STEP, obj.getPos(), GameEvent.Emitter.of(obj.getSteppingBlockState()));
				if (obj.isOnGround()) {
					obj.setVelocity(velocity.getX(), obj.getVelocity().getY(), velocity.getZ());
				} else {
					obj.setVelocity(velocity.getX() * 0.8, obj.getVelocity().getY(), velocity.getZ() * 0.8);
				}
				if (ticksSliding < 60) {
					ticksSliding++;
				}
			} else if (ticksSliding > 0) {
				ticksSliding = Math.max(0, ticksSliding - 4);
			}
		} else {
			velocity = Vec3d.ZERO;
			shouldSlam = false;
			jumpBoostResetTicks = DEFAULT_JUMP_BOOST_RESET_TICKS;
			slamCooldown = DEFAULT_SLAM_COOLDOWN;
			ticksLeftToJump = 0;
			ticksSliding = 0;
		}
	}

	@Override
	public void serverTick() {
		tick();
		if (hasSlide && shouldSlam) {
			slamTick(() -> {
				obj.getWorld().getOtherEntities(obj, new Box(obj.getBlockPos()).expand(5, 1, 5), foundEntity -> foundEntity.isAlive() && foundEntity.distanceTo(obj) < 5).forEach(entity -> {
					if (entity instanceof LivingEntity living && EnchancementUtil.shouldHurt(obj, living)) {
						living.takeKnockback(1, obj.getX() - living.getX(), obj.getZ() - living.getZ());
					}
				});
				obj.getWorld().emitGameEvent(GameEvent.STEP, obj.getPos(), GameEvent.Emitter.of(obj.getSteppingBlockState()));
			});
			EnchancementUtil.PACKET_IMMUNITIES.put(obj, 20);
		}
		EntityAttributeInstance attribute = obj.getAttributeInstance(EntityAttributes.GENERIC_STEP_HEIGHT);
		if (hasSlide && isSliding()) {
			if (!attribute.hasModifier(STEP_HEIGHT_INCREASE)) {
				attribute.addPersistentModifier(STEP_HEIGHT_INCREASE);
			}
			EnchancementUtil.PACKET_IMMUNITIES.put(obj, 20);
		} else if (attribute.hasModifier(STEP_HEIGHT_INCREASE)) {
			attribute.removeModifier(STEP_HEIGHT_INCREASE);
		}
	}

	@Override
	public void clientTick() {
		tick();
		if (hasSlide && !obj.isSpectator() && obj == MinecraftClient.getInstance().player) {
			if (shouldSlam) {
				slamTick(() -> {
					disallowSlide = true;
					BlockPos.Mutable mutable = new BlockPos.Mutable();
					for (int i = 0; i < 360; i += 15) {
						for (int j = 1; j < 5; j++) {
							double x = obj.getX() + MathHelper.sin(i) * j / 2, z = obj.getZ() + MathHelper.cos(i) * j / 2;
							BlockState state = obj.getWorld().getBlockState(mutable.set(x, Math.round(obj.getY() - 1), z));
							if (!state.isReplaceable() && obj.getWorld().getBlockState(mutable.move(Direction.UP)).isReplaceable()) {
								obj.getWorld().addParticle(new BlockStateParticleEffect(ParticleTypes.BLOCK, state), x, mutable.getY(), z, 0, 0, 0);
							}
						}
					}
				});
			}
			GameOptions options = MinecraftClient.getInstance().options;
			boolean pressingSlideKey = EnchancementClient.SLIDE_KEYBINDING.isPressed();
			if (!pressingSlideKey) {
				disallowSlide = false;
			}
			if (pressingSlideKey && !obj.isSneaking() && !disallowSlide) {
				if (canSlide()) {
					velocity = getVelocityFromInput(options).rotateY((float) Math.toRadians(-(obj.getHeadYaw() + 90)));
					SlideSetVelocityPayload.send(velocity);
				}
			} else if (velocity != Vec3d.ZERO) {
				velocity = Vec3d.ZERO;
				SlideResetVelocityPayload.send();
			}
			boolean pressingSlamKey = EnchancementClient.SLAM_KEYBINDING.isPressed();
			if (pressingSlamKey && !wasPressingSlamKey && canSlam()) {
				shouldSlam = true;
				slamCooldown = DEFAULT_SLAM_COOLDOWN;
				SlideSlamPayload.send();
			}
			wasPressingSlamKey = pressingSlamKey;
		} else {
			disallowSlide = false;
			wasPressingSlamKey = false;
		}
	}

	public void setVelocity(Vec3d velocity) {
		this.velocity = velocity;
	}

	public void setShouldSlam(boolean shouldSlam) {
		this.shouldSlam = shouldSlam;
	}

	public boolean shouldSlam() {
		return shouldSlam;
	}

	public void setSlamCooldown(int slamCooldown) {
		this.slamCooldown = slamCooldown;
	}

	public boolean isSliding() {
		return !velocity.equals(Vec3d.ZERO);
	}

	public boolean shouldBoostJump() {
		return ticksLeftToJump > 0;
	}

	public float getJumpBonus() {
		return MathHelper.lerp(ticksSliding / 60F, 1F, 3F);
	}

	public int getSlideLevel() {
		return slideLevel;
	}

	public boolean hasSlide() {
		return hasSlide;
	}

	public boolean canSlide() {
		return !isSliding() && obj.isOnGround() && EnchancementUtil.isGroundedOrAirborne(obj);
	}

	public boolean canSlam() {
		return slamCooldown == 0 && !isSliding() && !obj.isOnGround() && EnchancementUtil.isGroundedOrAirborne(obj);
	}

	private void slamTick(Runnable onLand) {
		obj.setVelocity(obj.getVelocity().getX() * 0.98, -3, obj.getVelocity().getZ() * 0.98);
		obj.fallDistance = 0;
		if (obj.isOnGround()) {
			shouldSlam = false;
			ticksLeftToJump = 5;
			obj.playSound(ModSoundEvents.ENTITY_GENERIC_IMPACT, 1, 1);
			onLand.run();
		}
	}

	private Vec3d getVelocityFromInput(GameOptions options) {
		boolean any = false, forward = false, sideways = false;
		int x = 0, z = 0;
		if (options.forwardKey.isPressed()) {
			any = true;
			forward = true;
			x = 1;
		}
		if (options.backKey.isPressed()) {
			any = true;
			forward = true;
			x = -1;
		}
		if (options.leftKey.isPressed()) {
			any = true;
			sideways = true;
			z = -1;
		}
		if (options.rightKey.isPressed()) {
			any = true;
			sideways = true;
			z = 1;
		}
		return new Vec3d(any ? x : 1, 0, z).multiply(forward && sideways ? 0.75F : 1).multiply(slideLevel * 0.5F);
	}
}
