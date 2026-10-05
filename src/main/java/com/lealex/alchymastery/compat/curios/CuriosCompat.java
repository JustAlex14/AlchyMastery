package com.lealex.alchymastery.compat.curios;

import com.lealex.alchymastery.registry.ModRegistries;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotResult;
import top.theillusivec4.curios.api.CuriosCapability;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurio;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

/** Curios integration (only called when Curios is loaded): a linked experience siphon worn in a curio slot. */
public final class CuriosCompat {
    private CuriosCompat() {}

    /**
     * The siphon is a curio: besides the charm tag (drag it into the slot), this lets a right-click with it in hand
     * equip it into a free charm slot, like armor (not while sneaking: that unlinks it).
     */
    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerItem(CuriosCapability.ITEM, (stack, context) -> new ICurio() {
            @Override
            public ItemStack getStack() {
                return stack;
            }

            @Override
            public boolean canEquipFromUse(SlotContext slotContext) {
                return !slotContext.entity().isShiftKeyDown(); // sneak + right-click in the air unlinks instead
            }
        }, ModRegistries.EXPERIENCE_SIPHON.get());
    }

    public static ItemStack findLinkedSiphon(Player player) {
        return CuriosApi.getCuriosInventory(player)
                .flatMap(curios -> curios.findFirstCurio(stack -> stack.is(ModRegistries.EXPERIENCE_SIPHON.get())
                        && stack.has(ModRegistries.LINKED_MACHINE.get())))
                .map(SlotResult::stack)
                .orElse(ItemStack.EMPTY);
    }
}
