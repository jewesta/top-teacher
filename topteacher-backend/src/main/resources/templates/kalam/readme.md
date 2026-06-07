# Kalam font subsets

The original Kalam TTF files are kept in this folder together with the SIL Open Font License file.
The `*-subset.ttf` files are generated from those originals for the PDF export.

The subsets remove glyphs that are not needed for TopTeacher's teacher notes, mainly large writing
systems outside the Latin script. This keeps generated PDFs much smaller while preserving the
characters expected for German, English, and Spanish text.

Included Unicode ranges:

- `U+0020-007E`: Basic Latin
- `U+00A0-024F`: Latin-1 Supplement, Latin Extended-A, Latin Extended-B
- `U+0300-036F`: Combining diacritical marks
- `U+1E00-1EFF`: Latin Extended Additional
- `U+2010-201F`: Dashes and quotation marks
- `U+2020-2027`: Common punctuation such as bullets and ellipsis
- `U+2030-203A`: Additional punctuation such as per mille and guillemets
- `U+20AC`: Euro sign
- `U+2212`: Mathematical minus sign

The subsets were generated with FontTools:

```shell
python -m fontTools.subset Kalam-Regular.ttf \
  --output-file=Kalam-Regular-subset.ttf \
  --unicodes=U+0020-007E,U+00A0-024F,U+0300-036F,U+1E00-1EFF,U+2010-201F,U+2020-2027,U+2030-203A,U+20AC,U+2212 \
  --no-hinting
```

The same command shape was used for `Kalam-Light.ttf` and `Kalam-Bold.ttf`.
