package com.lealex.alchymastery.compat.jade;

import com.lealex.alchymastery.Alchymastery;
import com.lealex.alchyx.block.entity.ChamberShellBlockEntity;
import com.lealex.alchymastery.block.entity.DistortionMatrixBlockEntity;
import com.lealex.alchyx.multiblock.CoreTracker;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;
import snownee.jade.api.Accessor;
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
 * of a formed chamber. (Naming a formed machine's parts after their machine is AlchyX's own Jade plugin now.)
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
