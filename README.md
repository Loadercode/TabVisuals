# TabVisuals

Client-side tab list visuals for **Minecraft 1.21.4** (Fabric).

## Requirements

- JDK **21**
- Minecraft **1.21.4**
- Fabric Loader **≥ 0.16.10**
- Fabric API

## Build

```bash
./gradlew build          # Linux / macOS
gradlew.bat build        # Windows
```

Artifact: `build/libs/tabvisuals-0.1.0.jar`

## Dev run

```bash
gradlew runClient
```

## Project layout

```
src/main/java/com/loadercode/tabvisuals/
├── TabVisualsClient.java          # client entrypoint, fonts pre-init, RSHIFT keybind
├── mixin/PlayerListHudMixin.java  # hook into the tab list render
├── mixin/TitleScreenMixin.java    # swaps vanilla title for the TabVisuals splash/menu
├── gui/                           # ClickGUI + title flow (own render stack, no vanilla widgets)
│   ├── ClickGuiScreen.java        # tabs, module cards, search, Configs, theme picker
│   ├── UiRender.java              # rect/outline/gradient/text helpers
│   ├── Category.java Theme.java   # categories + 5 built-in palettes
│   ├── title/TabVisualsTitleScreen.java  # splash → zoom transition → main menu, alt-manager modal
│   ├── music/MusicManager.java    # track/lyrics stubs (integration hooks)
│   ├── account/AccountManager.java# offline nick profiles, JSON + runtime session swap
│   ├── config/ConfigManager.java  # autosave + named configs (config/tabvisuals/*.json)
│   ├── module/                    # ModuleStub/ModuleRegistry (dummy modules)
│   └── setting/                   # Boolean/Double/Mode settings
└── render/                        # render 2D/3D + font stack (ported from LoxeDLC)
    ├── geometry/Render2D.java     # screen-space primitives
    ├── geometry/Render3D.java     # world-space lines/boxes/quads/billboards
    ├── font/FontRenderer.java     # TTF glyph rasterizer + text drawing
    ├── font/Fonts.java            # font registry (sf, manrope, mono, icons…)
    ├── atlasfont/                 # MSDF font pipeline
    ├── shape/ draw/ shader/       # rounded rects, blur, arcs, shader programs
    ├── color/                     # color/theme helpers
    ├── math/                      # projection + calc helpers
    └── stencil/ scissor/ item/ systemrender/
src/main/resources/
├── fabric.mod.json
├── tabvisuals.mixins.json
├── tabvisuals.accesswidener       # minimal: 6 render-path members
└── assets/
    ├── minecraft/fonts/           # 19 TTF/OTF faces
    ├── minecraft/shaders/core/    # round, blur, arc, hud_glass
    ├── mre/                       # MSDF shader set + atlas fonts
    ├── tabvisuals/lang/           # en_us / ru_ru (keybind names)
    └── tabvisuals/shaders/hand/   # hand shader set
```

## Controls

- **RSHIFT** — open ClickGUI (tabs, modules, Configs, theme picker)
- Title screen: any key / click → main menu; **ESC** — back; «Аккаунт» — offline nicks

## License

MIT — see [LICENSE](LICENSE).
