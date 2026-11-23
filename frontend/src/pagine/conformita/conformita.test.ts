import { describe, expect, it } from "vitest";
import type { Requisito } from "../../api/conformita";
import { coloreStato, raggruppaPerMisura } from "./conformita";

const requisito = (codice: string, misura: string): Requisito => ({
  id: codice.length,
  codice,
  numeroPunto: 1,
  testo: "",
  misuraCodice: misura,
  misuraCategoria: "Categoria",
  misuraDescrizione: "Descrizione",
  stato: null,
  dataValutazione: null,
  note: null,
});

describe("raggruppaPerMisura", () => {
  it("mette insieme i requisiti della stessa misura nell ordine ricevuto", () => {
    const gruppi = raggruppaPerMisura([
      requisito("GV.SC-04.1", "GV.SC-04"),
      requisito("ID.AM-01.1", "ID.AM-01"),
      requisito("GV.SC-04.2", "GV.SC-04"),
    ]);
    expect(gruppi.map((g) => g.codice)).toEqual(["GV.SC-04", "ID.AM-01"]);
    expect(gruppi[0]?.requisiti.map((r) => r.codice)).toEqual([
      "GV.SC-04.1",
      "GV.SC-04.2",
    ]);
  });
});

describe("coloreStato", () => {
  it("usa il verde per attuato, il rosso per non attuato e il neutro per da valutare", () => {
    expect(coloreStato("ATTUATO")).toBe("success");
    expect(coloreStato("NON_ATTUATO")).toBe("error");
    expect(coloreStato(null)).toBe("default");
  });
});
