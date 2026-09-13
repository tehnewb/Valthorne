package valthorne.website.export;

/**
 * Browser styles authored and exported by Java alongside the landing page.
 *
 * <p>The complete semantic layout remains available before graphics start, in
 * text mode, or when scripting is unavailable. Native links stay transparent
 * over the engine surface while retaining their normal keyboard behavior.</p>
 */
public final class BrowserStyles {
    private BrowserStyles() { }

    /** Returns the shared responsive layout, typography, and native-control styles. */
    public static String css() {
        return """
                @font-face {
                  font-family: UrbanistWebsite; src: url('assets/fonts/Urbanist-Variable.ttf') format('truetype');
                  font-style: normal; font-weight: 100 900; font-display: swap;
                }
                :root {
                  color-scheme: dark; font-family: UrbanistWebsite, Arial, sans-serif;
                  color: #fff; background: #101114; --paper: #f3f3ef; --ink: #151619;
                  --muted-light: #575b62; --muted-dark: #bfc3cb; --accent: #c7e6ff; --focus: #70839b;
                }
                * { box-sizing: border-box; }
                html { scroll-padding-top: 100px; }
                body { margin: 0; }
                a { color: inherit; text-decoration: none; }
                a, button { -webkit-tap-highlight-color: transparent; }
                a:focus-visible, button:focus-visible { outline: 3px solid var(--focus); outline-offset: 5px; }
                button { font: inherit; }
                ::selection { background: var(--accent); color: var(--ink); }
                [hidden] { display: none !important; }
                .wrap { width: min(1320px, calc(100% - 112px)); margin-inline: auto; }
                .skip-link { position: fixed; z-index: 20; left: 16px; top: -100px; padding: 14px 20px; color: var(--ink); background: var(--accent); }
                .skip-link:focus { top: 12px; }

                /* Java owns the painted interface. These elements provide native browser interaction. */
                #scene { display: none; width: 100vw; height: 100vh; touch-action: pan-y; }
                #valthorne-window { visibility: hidden; }
                .engine-ready #scene { display: block; }
                .engine-ready #valthorne-window { visibility: visible; }
                #engine-links { display: none; position: fixed; inset: 0; pointer-events: none; z-index: 3; }
                .engine-ready #engine-links { display: block; }
                .engine-link { position: absolute; pointer-events: auto; overflow: hidden; white-space: nowrap; border-radius: 2px; color: transparent; }
                .engine-link:link, .engine-link:visited, .engine-link:hover, .engine-link:active, .engine-link:focus-visible { color: transparent; }
                .engine-link:hover { background: #858c9c21; }
                .engine-link:focus-visible { outline-offset: 2px; }
                #scroll-space { display: none; }
                .engine-ready #scroll-space { display: block; }
                .engine-ready #content { position: absolute; top: 0; left: 0; width: 1px; height: 1px; overflow: hidden; clip-path: inset(50%); white-space: nowrap; }

                /* Compact masthead and a full-width cinematic opening. */
                .masthead { height: 80px; background: #101114; }
                .masthead .wrap { height: 100%; display: flex; align-items: center; gap: 56px; }
                .brand { display: inline-flex; flex-shrink: 0; align-items: center; gap: 11px; font-size: 25px; line-height: 1; font-weight: 750; letter-spacing: -.04em; }
                .brand img { width: 27px; height: 43px; object-fit: contain; }
                .masthead nav { display: flex; align-items: center; gap: 36px; }
                .masthead nav a { display: inline-flex; min-height: 44px; align-items: center; font-size: 15px; font-weight: 550; }
                .masthead nav a:hover { text-decoration: underline; text-underline-offset: 6px; }
                .masthead .action { margin-left: auto; }
                .action { display: inline-flex; align-items: center; justify-content: center; gap: 28px; min-height: 50px; padding: 12px 24px; border: 1px solid #ffffff70; border-radius: 2px; font-size: 16px; line-height: 1.3; font-weight: 650; }
                .action.primary { color: #151619; background: var(--accent); border-color: var(--accent); }
                .action:hover { background: #ffffff15; }
                .action.primary:hover { background: #e1f1ff; border-color: #e1f1ff; }
                .masthead .action { min-height: 42px; padding: 9px 19px; font-size: 14px; }
                .hero { position: relative; min-height: 690px; height: min(860px, calc(100svh - 110px)); overflow: hidden; isolation: isolate; }
                .hero-media { position: absolute; inset: 0; margin: 0; z-index: -2; }
                .hero-media img { width: 100%; height: 100%; display: block; object-fit: cover; object-position: center; }
                .hero::after { content: ''; position: absolute; inset: 0; z-index: -1; background: linear-gradient(0deg,#101114e8 0%,#10111445 55%,#10111400 85%); }
                .hero .wrap { height: 100%; display: flex; flex-direction: column; justify-content: flex-end; padding-block: 90px 68px; }
                .eyebrow { margin: 0 0 22px; font-size: 11px; font-weight: 650; line-height: 1.5; letter-spacing: .12em; }
                h1 { font-size: clamp(88px, 9.3vw, 144px); font-weight: 550; line-height: .98; letter-spacing: -.055em; margin: 0 0 24px; }
                .hero-copy { max-width: 580px; margin: 0; font-size: 20px; line-height: 1.55; color: #eef0f4; }
                .hero-actions { display: flex; flex-wrap: wrap; gap: 14px; margin-top: 30px; }
                .hero-caption { margin: 34px 0 0; font-size: 12px; color: #cbd0d8; }

                /* Paper surfaces make the engine's capabilities easy to scan. */
                #engine { color-scheme: light; background: var(--paper); color: var(--ink); padding-block: 112px 108px; }
                .section-intro { display: grid; grid-template-columns: 1.1fr .9fr; align-items: end; gap: 80px; margin-bottom: 72px; }
                h2 { font-size: clamp(48px, 5.4vw, 76px); font-weight: 550; line-height: 1.04; letter-spacing: -.045em; white-space: pre-line; margin: 0; }
                .section-intro > p { margin: 0 0 4px; font-size: 21px; line-height: 1.6; color: var(--muted-light); }
                .feature { display: grid; grid-template-columns: 240px 1fr; column-gap: 68px; padding-block: 42px; border-top: 1px solid #bfc2c4; }
                .feature:last-child { padding-bottom: 0; }
                .feature-number { margin: 5px 0 0; font-size: 12px; letter-spacing: .07em; font-weight: 650; color: var(--muted-light); }
                h3 { font-size: 34px; line-height: 1.16; letter-spacing: -.025em; font-weight: 600; margin: 0 0 16px; }
                .feature p:not(.feature-number) { max-width: 730px; color: var(--muted-light); font-size: 18px; line-height: 1.65; margin: 0 0 16px; }
                .text-link { display: inline-flex; min-height: 44px; align-items: center; gap: 14px; font-size: 15px; font-weight: 650; }
                .text-link:hover { text-decoration: underline; text-underline-offset: 5px; }

                /* Resources and the final invitation use open columns and generous spacing. */
                #resources { padding-block: 108px 110px; }
                #resources .section-intro > p { color: var(--muted-dark); }
                .resources { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 44px; }
                .resource { display: flex; flex-direction: column; border-top: 1px solid #5b6068; padding-top: 32px; }
                .resource h3 { font-size: 29px; }
                .resource p { color: var(--muted-dark); font-size: 18px; line-height: 1.6; margin: 0 0 22px; }
                .resource .text-link { margin-top: auto; color: var(--accent); }
                #start .wrap { border-top: 1px solid #444952; padding-block: 92px 100px; }
                #start h2 { max-width: 1000px; font-size: clamp(64px, 7vw, 104px); }
                #start p { color: var(--muted-dark); font-size: 20px; line-height: 1.6; margin: 28px 0; }
                .project-footer { border-top: 1px solid #444952; padding-block: 42px 36px; }
                .project-footer .wrap { display: flex; justify-content: space-between; gap: 30px; align-items: center; }
                .project-footer p { margin: 0; font-size: 13px; line-height: 1.6; color: var(--muted-dark); }
                .project-footer p a { text-decoration: underline; text-underline-offset: 3px; }

                /* Browser utilities live in the footer; only Back to top floats over the page. */
                #access-bar { display: flex; align-items: center; flex-wrap: wrap; gap: 10px 24px; width: min(1320px, calc(100% - 112px)); margin: 0 auto; padding: 12px 0 24px; color: var(--muted-dark); font-size: 12px; }
                .engine-ready #access-bar { position: absolute; left: 0; right: 0; z-index: 4; }
                #status { margin-right: auto; }
                #access-bar a { display: inline-flex; min-height: 44px; align-items: center; }
                #access-bar a:hover { color: #fff; text-decoration: underline; text-underline-offset: 4px; }
                #motion-toggle { display: none; align-items: center; justify-content: center; gap: 9px; min-height: 44px; padding: 8px 12px; color: var(--muted-dark); border: 1px solid #5b6068; border-radius: 2px; background: transparent; font-size: 12px; cursor: pointer; }
                .engine-ready #motion-toggle { display: inline-flex; }
                #motion-toggle:hover { border-color: #bfc3cb; color: #fff; }
                #motion-toggle:disabled { cursor: default; opacity: .7; }
                #motion-toggle .play-icon, .motion-paused #motion-toggle .pause-icon { display: none; }
                .motion-paused #motion-toggle .play-icon { display: inline; }
                #back-top { display: none; position: fixed; right: 22px; bottom: 22px; z-index: 5; width: 44px; height: 44px; border: 1px solid #858d9a; border-radius: 2px; background: #151619; color: #fff; font-size: 21px; cursor: pointer; }
                .engine-ready #back-top { display: block; }
                #back-top:hover { background: #292d34; }
                .not-found { min-height: 75vh; padding-block: 120px; }
                .not-found h1 { max-width: 1000px; font-size: clamp(50px,7vw,90px); }
                .not-found p { margin: 26px 0 32px; color: var(--muted-dark); font-size: 20px; }
                @media (max-width: 1050px) {
                  .wrap, #access-bar { width: calc(100% - 72px); }
                  .section-intro { gap: 44px; }
                  .feature { grid-template-columns: 180px 1fr; column-gap: 36px; }
                  .resources { gap: 28px; }
                  .masthead .wrap { gap: 40px; }
                  .masthead nav { gap: 25px; }
                }
                @media (max-width: 760px) {
                  html { scroll-padding-top: 130px; }
                  .wrap, #access-bar { width: calc(100% - 48px); }
                  .masthead { height: 112px; }
                  .masthead .wrap { display: grid; grid-template-columns: 1fr auto; grid-template-rows: 64px 48px; gap: 0 20px; }
                  .brand { font-size: 22px; gap: 8px; }
                  .brand img { width: 23px; height: 37px; }
                  .masthead nav { grid-row: 2; grid-column: 1 / -1; justify-content: space-between; gap: 16px; }
                  .masthead nav a { font-size: 14px; }
                  .masthead .action { grid-row: 1; grid-column: 2; padding-inline: 15px; }
                  .hero { height: 760px; min-height: 760px; }
                  .hero-media img { object-position: 87% center; }
                  .hero::after { background: linear-gradient(0deg,#101114 0%,#101114cc 34%,#10111412 77%); }
                  .hero .wrap { padding-block: 80px 50px; }
                  .eyebrow { font-size: 10px; letter-spacing: .1em; margin-bottom: 20px; }
                  h1 { font-size: clamp(62px, 15vw, 106px); line-height: 1; margin-bottom: 24px; }
                  .hero-copy { font-size: 18px; max-width: 520px; }
                  .hero-actions { gap: 10px; margin-top: 26px; }
                  .action { padding-inline: 20px; font-size: 15px; gap: 14px; }
                  .hero-caption { margin-top: 30px; font-size: 11px; }
                  #engine, #resources { padding-block: 68px; }
                  .section-intro { grid-template-columns: 1fr; gap: 26px; margin-bottom: 42px; }
                  h2 { font-size: clamp(46px, 10vw, 66px); }
                  .section-intro > p { font-size: 19px; line-height: 1.6; }
                  .feature { grid-template-columns: 1fr; gap: 20px; padding-block: 32px; }
                  .feature-number { margin: 0; font-size: 11px; }
                  h3 { font-size: 29px; }
                  .feature p:not(.feature-number), .resource p { font-size: 17px; }
                  .resources { grid-template-columns: 1fr; gap: 32px; }
                  .resource { padding-top: 27px; }
                  .resource p { margin-bottom: 12px; }
                  #start .wrap { padding-block: 62px 70px; }
                  #start h2 { font-size: clamp(54px, 12vw, 84px); }
                  #start p { font-size: 18px; margin-block: 24px; }
                  .project-footer { padding-block: 32px 20px; }
                  .project-footer .wrap { flex-direction: column; align-items: flex-start; gap: 22px; }
                  .project-footer p { max-width: 300px; }
                  #access-bar { gap: 6px 18px; padding-bottom: 22px; padding-right: 38px; }
                  #status { flex-basis: 100%; }
                  #back-top { right: 16px; bottom: 16px; }
                  .not-found { padding-block: 90px; }
                }
                @media (prefers-reduced-motion: reduce) { *, *::before, *::after { scroll-behavior: auto !important; animation: none !important; transition-duration: 0s !important; } }
                .motion-paused *, .motion-paused *::before, .motion-paused *::after { scroll-behavior: auto !important; animation: none !important; transition-duration: 0s !important; }
                """;
    }
}
