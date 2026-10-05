package com.lealex.alchymastery.compat.jade;

import com.lealex.alchymastery.Alchymastery;
import com.lealex.alchyx.block.entity.ChamberShellBlockEntity;
import com.lealex.alchymastery.block.entity.DistortionMatrixBlockEntity;
import com.lealex.alchyx.animation.LoadedCores;
import com.lealex.alchyx.block.ChamberShellBlock;
import com.lealex.alchyx.block.entity.MultiblockCoreBlockEntity;
import com.lealex.alchyx.multiblock.CoreTracker;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;
import snownee.jade.api.Accessor;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.JadeIds;
import snownee.jade.api.config.IPluginConfig;
import snownee.jade.api.theme.IThemeHelper;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;
import snownee.jade.api.view.ClientViewGroup;
import snownee.jade.api.view.EnergyView;
import snownee.jade.api.view.IClientExtensionProvider;
import snownee.jade.api.view.IServerExtensionProvider;
import snownee.jade.api.view.ViewGroup;

import java.util.List;

/**
 * Jade integration (only loaded when Jade is installed: Jade finds this class by its @WailaPlugin annotation,
 * so nothing else in the mod refers to it and the mod still runs without Jade).
 *
 * Shows the distortion chamber's energy as Jade's standard energy bar, in DE, on the matrix and on every part
 * of a formed chamber; and names a formed machine's parts after their machine ("Destructuration Chamber")
 * instead of AlchyX's generic "Machine Shell".
 */
@WailaPlugin
public class AlchymasteryJadePlugin implements IWailaPlugin {

    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerEnergyStorage(DistortionEnergyProvider.INSTANCE, DistortionMatrixBlockEntity.class);
        registration.registerEnergyStorage(DistortionEnergyProvider.INSTANCE, ChamberShellBlockEntity.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerEnergyStorageClient(DistortionEnergyProvider.INSTANCE);
        registration.registerBlockComponent(MachineNameProvider.INSTANCE, ChamberShellBlock.class);
    }

    /**
     * Client: replaces a shell part's name with its machine's: lang key multiblock.<namespace>.<pattern>, or the
     * pattern's file name ("rendering_cauldron" -> "Rendering Cauldron"), so KubeJS machines get a name too.
     * Finds the machine among the cores loaded on the client (formed is synced with them).
     */
    public enum MachineNameProvider implements IBlockComponentProvider {
        INSTANCE;

        private static final Identifier UID = Identifier.fromNamespaceAndPath(Alchymastery.MODID, "machine_name");
        private static final double SEARCH = 16 * 16; // squared: the biggest machines are 7 wide

        @Override
        public Identifier getUid() {
            return UID;
        }

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            BlockPos pos = accessor.getPosition();
            for (MultiblockCoreBlockEntity core : LoadedCores.snapshot()) {
                if (core.getLevel() != accessor.getLevel() || core.isRemoved() || !core.isFormed()) continue;
                if (core.getBlockPos().distSqr(pos) > SEARCH || !core.structureContains(pos)) continue;
                tooltip.replace(JadeIds.CORE_OBJECT_NAME, IThemeHelper.get().title(machineName(core.patternId())));
                return;
            }
        }

        public static Component machineName(Identifier pattern) {
            String[] words = pattern.getPath().split("_");
            StringBuilder fallback = new StringBuilder();
            for (String word : words) {
                if (word.isEmpty()) continue;
                if (!fallback.isEmpty()) fallback.append(' ');
                fallback.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
            }
            return Component.translatableWithFallback("multiblock." + pattern.getNamespace() + "." + pattern.getPath(), fallback.toString());
        }
    }

    /**
     * Server half (getGroups): reads the matrix's energy when a player looks at the block; Jade sends it to them.
     * Client half (getClientGroups): turns the numbers into the bar text, e.g. "12.5k / 1G DE".
     */
    public enum DistortionEnergyProvider
            implements IServerExtensionProvider<EnergyView.Data>, IClientExtensionProvider<EnergyView.Data, EnergyView> {
        INSTANCE;

        private static final Identifier UID = Identifier.fromNamespaceAndPath(Alchymastery.MODID, "distortion_energy");
        private static final String UNIT = "DE";

        @Override
        public Identifier getUid() {
            return UID;
        }

        @Override
        public @Nullable List<ViewGroup<EnergyView.Data>> getGroups(Accessor<?> accessor) {
            DistortionMatrixBlockEntity matrix = switch (accessor.getTarget()) {
                case DistortionMatrixBlockEntity m -> m;
                case ChamberShellBlockEntity shell when shell.getLevel() != null
                        && CoreTracker.formedCoreAt(shell.getLevel(), shell.getBlockPos()) instanceof DistortionMatrixBlockEntity m -> m;
                case null, default -> null;
            };
            if (matrix == null) return null;
            EnergyView.Data data = new EnergyView.Data(matrix.getEnergy(), DistortionMatrixBlockEntity.MAX_ENERGY);
            return List.of(new ViewGroup<>(List.of(data)));
        }

        @Override
        public List<ClientViewGroup<EnergyView>> getClientGroups(Accessor<?> accessor, List<ViewGroup<EnergyView.Data>> groups) {
            return ClientViewGroup.map(groups, data -> EnergyView.read(data, UNIT), null);
        }
    }
}
