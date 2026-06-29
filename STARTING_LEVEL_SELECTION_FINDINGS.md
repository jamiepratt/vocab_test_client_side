# Starting Level Selection Findings

Checked on June 29, 2026 against the local dev app at `http://localhost:8000/index.html#/`.

## Result

All start-level selections load the first quiz item successfully. No selection showed `Could not load sentence questions`.

| Selection | Browser result | Served surface-rank window | First sentence |
| --- | --- | --- | --- |
| Absolute beginner / pre-A1 | Pass | `1-80` | `On tego nie zrobił.` |
| A1 | Pass | `401-480` | `Przyszedł mimo deszczu.` |
| A2 | Pass | `1,001-1,080` | `Przyjdę akurat po pracy.` |
| B1 | Pass | `2,001-2,080` | `Słyszałem nowe wieści rano.` |
| B2 | Pass | `4,001-4,080` | `Ja przysięgam że mówię prawdę.` |
| C1 | Pass | `8,001-8,080` | `Nie zgolił brody od miesiąca.` |
| C2 | Pass | `8,001-8,080` | `Nie zgolił brody od miesiąca.` |

## Notes

- Each first question showed `0 / 80 scored`, `Item 1 of 80`, five answer choices, and a `don't know` button.
- C1 and C2 currently load the same first sentence because backend metadata maps both `level=c1` and `level=c2` block `0` to surface ranks `8001-8080`.
- This is now valid for C2 after the backend fix from `12001` to `8001`; local sentence data tops out below `12001`, so the old C2 start could not load questions.
