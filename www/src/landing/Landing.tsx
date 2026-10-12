import { useEffect, useRef, useState } from "react";
import { LINKS } from "../lib/config";
import { loadCatalogue, useApp } from "../lib/store";
import { Icon, LensMark, Link, VerdictMark, VERDICTS } from "../ui";

/** Fades sections up as they scroll into view, and lifts the preview slightly as the page scrolls. */
const useLandingMotion = (root: React.RefObject<HTMLDivElement | null>, setScrolled: (value: boolean) => void) => {
  useEffect(() => {
    const element = root.current;
    if (!element) return;
    const targets = element.querySelectorAll(".reveal, .stagger, .pop");
    const observer = new IntersectionObserver(
      (entries) =>
        entries.forEach((entry) => {
          if (!entry.isIntersecting) return;
          entry.target.classList.add("in");
          observer.unobserve(entry.target);
        }),
      { rootMargin: "0px 0px -14% 0px" },
    );
    targets.forEach((target) => observer.observe(target));

    const still = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
    const mock = element.querySelector<HTMLElement>(".browser");
    let frame = 0;
    const onScroll = () => {
      cancelAnimationFrame(frame);
      frame = requestAnimationFrame(() => {
        setScrolled(window.scrollY > 8);
        if (mock && !still) mock.style.transform = `translateY(${-Math.min(48, window.scrollY / 12)}px)`;
      });
    };
    window.addEventListener("scroll", onScroll, { passive: true });
    return () => {
      observer.disconnect();
      window.removeEventListener("scroll", onScroll);
      cancelAnimationFrame(frame);
    };
  }, [root, setScrolled]);
};

export const Landing = () => {
  const root = useRef<HTMLDivElement>(null);
  const [scrolled, setScrolled] = useState(false);
  const { meta } = useApp();
  useLandingMotion(root, setScrolled);
  useEffect(() => {
    void loadCatalogue();
    document.title = "Label Lens: know what you are about to eat";
  }, []);

  // Real numbers once the catalogue has loaded; wording that is true either way until then.
  const count = meta ? `${meta.totalCount} additives` : "more than 500 additives";
  const sources = meta ? `${meta.sources}, updated ${meta.updatedLabel}` : "EFSA, FDA and JECFA";

  return (
    <div ref={root} id="top">
      <header className={`l-header ${scrolled ? "scrolled" : ""}`}>
        <div className="wrap">
          <a href="#top" className="brand">
            <LensMark vein="var(--bg)" />
            Label Lens
          </a>
          <nav className="l-nav" aria-label="Sections">
            <a href="#how">How it works</a>
            <a href="#get">Get Label Lens</a>
            <a href="#privacy">Privacy</a>
          </nav>
          <Link to="/app" className="btn btn-primary">Open the app</Link>
        </div>
      </header>

      <main>
        <section className="wrap hero">
          <div className="hero-copy">
            <span className="badge"><Icon name="document_scanner" size={18} />Food additive scanner</span>
            <h1>Know what you are about to eat.</h1>
            <p className="hero-lead">
              Photograph an ingredients list and Label Lens finds every additive on it, then tells you what each one is, where it's banned, and whether it suits your allergies and diet.
            </p>
            <div className="hero-actions">
              <Link to="/app" className="btn btn-primary btn-hero">Try it out<Icon name="arrow_forward" /></Link>
              <a href="#get" className="btn btn-outline btn-hero"><Icon name="android" size={22} />Get the Android app</a>
            </div>
            <div className="hero-note"><Icon name="check_circle" size={18} fill className="text-safe" />Free on the web · no account, no limits</div>
          </div>
          <div className="hero-mock">
            <div className="browser">
              <div className="browser-bar"><i /><i /><i /><span className="browser-url">label-lens.cc/app</span></div>
              <div className="browser-view">
                <iframe src="/app?preview" title="Label Lens app preview" tabIndex={-1} loading="lazy" aria-hidden="true" />
              </div>
            </div>
          </div>
        </section>

        <section id="how" className="wrap l-section">
          <div className="section-head reveal">
            <div className="kicker">HOW IT WORKS</div>
            <h2>From label to answer in three steps.</h2>
          </div>
          <div className="cards stagger">
            <div className="card"><span className="step-number">1</span><h3>Capture the label</h3><p>Photograph the ingredients list, or upload a photo you already took. Crop to the list for a faster read.</p></div>
            <div className="card"><span className="step-number">2</span><h3>Confirm the matches</h3><p>We match E-numbers and names, even misspelt ones, against {count}. Tick the ones we found.</p></div>
            <div className="card"><span className="step-number">3</span><h3>Read the details</h3><p>Evidence, sources and where it's banned, with a clear verdict for the whole product.</p></div>
          </div>
          <div className="verdicts reveal">
            <div className="verdicts-copy">
              <h3>One verdict you can read at a glance</h3>
              <p>Each level has its own colour and its own shape, so verdicts still read in greyscale. The verdict is checked against your own allergies and diet.</p>
            </div>
            <div className="verdict-samples stagger pop">
              {(["safe", "caution", "avoid"] as const).map((verdict) => (
                <div className="verdict-sample" key={verdict}>
                  <VerdictMark verdict={verdict} size={72} />
                  <span className={`text-${verdict}`}>{VERDICTS[verdict].label}</span>
                </div>
              ))}
            </div>
          </div>
        </section>

        <section id="get" className="wrap l-section">
          <div className="section-head center reveal">
            <div className="kicker">GET LABEL LENS</div>
            <h2>Use it on the web, or take it with you.</h2>
            <p className="muted" style={{ font: '500 16px/24px "Hanken Grotesk"' }}>Every feature, on both. No account, no subscriptions.</p>
          </div>
          <div className="plans stagger">
            <div className="plan">
              <Icon name="language" size={36} style={{ color: "var(--p)" }} />
              <h3>Web</h3>
              <div className="plan-price">Free</div>
              <p>Upload a photo of a label or search the database from any browser.</p>
              <Link to="/app" className="btn btn-primary">Try it out<Icon name="arrow_forward" size={20} /></Link>
            </div>
            <div className="plan plan-android">
              <Icon name="android" size={36} />
              <h3>Android</h3>
              <div className="plan-price">$9.99<small>one-time</small></div>
              <p>Scan with your camera in the shop. Works offline. Pay once on Google Play.</p>
              <a href={LINKS.play} target="_blank" rel="noopener" className="btn btn-primary">Get it on Google Play<Icon name="open_in_new" size={20} /></a>
            </div>
          </div>
        </section>

        <section id="privacy" className="privacy">
          <div className="wrap">
            <div className="privacy-head reveal">
              <div className="kicker">PRIVACY AND SOURCES</div>
              <h2>Your labels stay yours.</h2>
              <p>No account, and no photo ever leaves your device. And you don't have to take our word for it.</p>
            </div>
            <div className="facts stagger">
              <div className="fact">
                <span className="fact-icon" style={{ background: "var(--tc)", color: "var(--otc)" }}><Icon name="shield_lock" size={30} fill /></span>
                <div>
                  <h3>Labels are read on your device</h3>
                  <p>Photos and the text on them never leave it. Your list, history and preferences stay in your browser. Looking up an additive asks our server for its page; the server records your IP address, the kind of device and which additive it was, and nothing else.</p>
                </div>
              </div>
              <div className="fact">
                <span className="fact-icon" style={{ background: "var(--pc)", color: "var(--opc)" }}><Icon name="database" size={30} /></span>
                <div>
                  <h3>Sourced, not guessed</h3>
                  <p>Every entry links to its sources: {sources}. Information, not medical advice.</p>
                </div>
              </div>
              <div className="fact">
                <span className="fact-icon" style={{ background: "var(--sc)", color: "var(--osc)" }}><Icon name="code" size={30} /></span>
                <div>
                  <h3>Open source</h3>
                  <p>The full code is public, so anyone can check how labels are read and what the app does with your data.</p>
                  <a href={LINKS.repository} target="_blank" rel="noopener" className="source-link">View the source<Icon name="open_in_new" size={18} /></a>
                </div>
              </div>
            </div>
          </div>
        </section>

        <section className="wrap l-section">
          <div className="cta reveal">
            <div>
              <h2>Check your next label.</h2>
              <p>Free on the web. No account, no limits.</p>
            </div>
            <Link to="/app" className="btn btn-hero">Try it out<Icon name="arrow_forward" /></Link>
          </div>
        </section>
      </main>

      <footer className="l-footer">
        <div className="wrap">
          <div className="copyright"><LensMark size={24} />© {new Date().getFullYear()} Label Lens</div>
          <nav aria-label="Footer">
            <a href={LINKS.help}>Help</a>
            <a href={LINKS.licence}>Licence</a>
            <a href={LINKS.legal}>Legal</a>
            <a href={LINKS.contributing}>Contributing</a>
          </nav>
        </div>
      </footer>
    </div>
  );
};
