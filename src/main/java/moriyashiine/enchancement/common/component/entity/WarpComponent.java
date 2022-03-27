package moriyashiine.enchancement.common.component.entity;

import dev.onyxstudios.cca.api.v3.component.sync.AutoSyncedComponent;
import dev.onyxstudios.cca.api.v3.component.tick.ClientTickingComponent;
import moriyashiine.enchancement.common.registry.ModComponents;
import net.minecraft.entity.projectile.TridentEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;

public class WarpComponent implements AutoSyncedComponent, ClientTickingComponent {
	private final TridentEntity obj;
	private boolean shouldSpawnParticles = false;

	public WarpComponent(TridentEntity obj) {
		this.obj = obj;
	}

	@Override
	public void readFromNbt(NbtCompound tag) {
		shouldSpawnParticles = tag.getBoolean("ShouldSpawnParticles");
	}

	@Override
	public void writeToNbt(NbtCompound tag) {
		tag.putBoolean("ShouldSpawnParticles", shouldSpawnParticles);
	}

	@Override
	public void clientTick() {
		if (shouldSpawnParticles) {
			for (int i = 0; i < 8; i++) {
				obj.world.addParticle(ParticleTypes.REVERSE_PORTAL, obj.getParticleX(1), obj.getRandomBodyY(), obj.getParticleZ(1), 0, 0, 0);
			}
		}
	}

	public void setShouldSpawnParticles(boolean shouldSpawnParticles) {
		this.shouldSpawnParticles = shouldSpawnParticles;
		ModComponents.WARP.sync(obj);
	}
}
