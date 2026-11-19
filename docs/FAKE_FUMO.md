# Replica Fumo

`sh_common_lib:fake_fumo` is a static decorative replica of the existing first-party Dishanhai Fumo. It references the original baked block model and textures, credited to dishanhai, without copying assets or altering either real Fumo.

- Obtain it in the Colored Blocks creative tab or with `/give @s sh_common_lib:fake_fumo`.
- Placement faces the player; rotation/mirror and vanilla waterlogging are supported.
- Right-click with an empty main hand while not sneaking plays a quiet vanilla wool-hit sound. It does not change the block, start dancing, or open a screen. Held-item and sneak interactions pass through normally.
- The block has no block entity, ticker, custom renderer, networking, inventory, buffs or recipes. Breaking it uses a standard self-drop loot table (normal explosion survival applies).
- Existing Dishanhai and BeiBeiPuo Fumos and the ULK Fumo API are unchanged.

Supported source trees: Forge 1.20.1, NeoForge 1.21.1 and NeoForge 26.1.2. Run `python3 tools/verify_fake_fumo.py` for dependency-free asset and registration contract checks. This checks resources and source wiring, not in-game rendering or sound delivery; normal platform builds and in-game checks remain necessary.
