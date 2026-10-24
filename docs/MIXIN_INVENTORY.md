# SSC integration mixins

## FeralRenderFormMixin / FeralModelMixin / FeralTransformAnimationMixin

- `FormTextureUtils.getPlayerForm_Render`, RETURN, `remap = false`: per-player presentation view with FERAL body type and configured animation controller; never changes the real player form. SSC mouth items, held-item hiding, pose and other render consumers use this entry point. Explicit preview overrides remain respected.
- `FormRenderUtils.getPlayerAllFormRenderer`, HEAD: use the presentation model layer when active, enabling optional model replacement without an Origins change. Client only.
- `TransformingController.isEnabled/getAnimation`, HEAD: play SSC normal→feral / feral→normal animation for the synchronized cosmetic transition. Client only.
- Regression: both whitelist forms retain FormID, ability instances, perk tree while render body type changes; restoration and real form changes clear the presentation. Persisted state and recipes are checked in the server test mod. Client visuals require in-game QA.

## FormAttunerBlockMixin / PrimalPerkScreenMixin

- `FormAttunerBlock.openScreen`, HEAD, cancellable, `remap = false`: reject primal ocelot attunement on the server before recording the beacon position or sending the menu packet.
- `MinecraftClient.setScreen`, HEAD, cancellable, normal Minecraft remapping: reject `FormUpgradeScreen` for primal ocelots, covering the perk key and packet-driven opening.
- Both paths display the localized `ssc-primalstinct.attunement.blocked` info message. Normal ocelots are unaffected; primal ocelots use SSC's empty perk tree.
- Regression: learned ordinary ocelot perk → primal transformation/reload does not grant it; beacon interaction does not record attunement; switching back retains ordinary perks and beacon access. Client menu rejection still needs in-game visual verification.

## FormUtilsMixin

- Target: `FormUtils.reApplyPower(PlayerEntity)`, RETURN, `remap = false` (SSC method name).
- Purpose: queue the existing server-side power reconciliation after SSC rebuilds origin, perk and accessory powers without a form-change event.
- Behavior: restore addon baseline masks after perk reset or accessory power refresh; leave other power sources intact.
- Regression: awakened ocelot → SSC `reApplyPower` → process pending reconciliation → original pounce/dodge remain masked, addon replacements and unlocked perks remain present.


Feral crawling compatibility uses source-scoped power removal configured by `remove_powers`; no CrawlingPower mixin remains.
