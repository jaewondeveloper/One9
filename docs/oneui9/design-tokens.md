# One UI 9 design tokens (observed)

Values below were read from the resource tables of Samsung Internet 31.0.2.30
(`basic_token_sys_*` and `sesl_*`) for use as a visual reference. No layouts,
drawables or code from that app are included in this repository.

Observation: in One UI 9 the SESL colours are defined through role-based
`basic_token_sys_*` tokens rather than fixed hex values, so this library exposes
the same roles as `oui_des_sys_*` (`lib/src/main/res/values{,-night}/oui_des_sys_tokens.xml`).

## System tokens (light / dark)

| token (`oui_des_sys_*`) | light | dark |
|---|---|---|
| `background` | `#fff3f3f5` | `#ff010102` |
| `background_variant` | `#fffcfcff` | `#ff010102` |
| `green` | `#ff11a85f` | `#ff11a85f` |
| `green_bright` | `#ff11a85f` | `#ff5ae0a0` |
| `on_surface_container` | `#ff4d4d52` | `#ffccccce` |
| `on_surface_container_fixed` | `#fffcfcff` | `#fffcfcff` |
| `on_surface_container_fixed_variant` | `#ff010102` | `#ff010102` |
| `on_surface_container_high` | `#ff252528` | `#ffe9e9ec` |
| `on_surface_container_highest` | `#ff010102` | `#fffcfcff` |
| `on_surface_container_low` | `#ff636368` | `#ffb7b7bb` |
| `on_surface_container_lowest` | `#ff848487` | `#ffa3a3a7` |
| `orange` | `#ffe65b17` | `#ffe65b17` |
| `orange_bright` | `#ffe65b17` | `#fff58249` |
| `outline` | `#ffccccce` | `#ff636368` |
| `outline_high` | `#ff848487` | `#ffa3a3a7` |
| `outline_highest` | `#ff252528` | `#ffe9e9ec` |
| `outline_low` | `#ffe4e4e7` | `#ff3a3a3d` |
| `primary` | `#ff387aff` | `#ff387aff` |
| `primary_bright` | `#ff387aff` | `#ff598fff` |
| `primary_high` | `#ff2e65d4` | `#ff598fff` |
| `red` | `#ffd93e36` | `#ffd93e36` |
| `red_bright` | `#ffd93e36` | `#fff56962` |
| `surface` | `#fffcfcff` | `#ff17171a` |
| `surface_bright` | `#fffcfcff` | `#ff252528` |
| `surface_brightest` | `#fffcfcff` | `#ff3a3a3d` |
| `surface_container` | `#ffe4e4e7` | `#ff3a3a3d` |
| `surface_container_fixed` | `#fffcfcff` | `#fffcfcff` |
| `surface_container_fixed_variant` | `#ff010102` | `#ff010102` |
| `surface_container_high` | `#ffccccce` | `#ff4d4d52` |
| `surface_container_higher` | `#ffa3a3a7` | `#ff636368` |
| `surface_container_highest` | `#ff848487` | `#ff848487` |
| `surface_container_low` | `#fff3f3f5` | `#ff252528` |
| `surface_container_lowest` | `#fffcfcff` | `#ff010102` |
| `surface_fixed` | `#ff4d4d52` | `#ff4d4d52` |
| `surface_fixed_variant` | `#ff010102` | `#ff010102` |
| `surface_variant` | `#ffe9e9ec` | `#ff17171a` |
| `tone_on_tone` | `#0d17171a` | `#1afcfcff` |
| `tone_on_tone_high` | `#1a17171a` | `#26fcfcff` |

## Corner radius scale

xs 8dp, sm 12dp, md 18dp, lg 22dp, xl 26dp, full 999dp (`oui_des_radius_*`).
Dialog/menu/floating-pane backgrounds use xl (26dp), drawer and hover popups lg (22dp).

## SESL colour overrides

`oui_des_sesl_overrides.xml` redefines only the flat `sesl_*` colours this library
references, so SESL 8 widgets pick up the One UI 9 values. State-list colours
(`sesl_primary_text_*`, `sesl_btn_default_color_*`, `sesl_tablayout_selected_*`) are not overridden:
they resolve to `sesl_*_color_light/dark` values that still need checking against SESL 8.
Full observed table: `sesl-colors.json` (name -> [light, dark]).
