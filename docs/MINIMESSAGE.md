# MiniMessage Guide

CraftedGateway supports MiniMessage for VOTD text formatting. This lets you control colors and basic styling in config strings.

## Where MiniMessage Applies
- `votd.message-format`
- `votd.join-format`
- `votd.random-announcement-format`

If a template contains MiniMessage tags (for example `<gold>`), it is parsed as MiniMessage. If it only uses legacy `&` color codes, it is parsed as legacy.

## Placeholders
You can use these placeholders in any template:
- `{reference}` - the verse reference (ex: `John 3:16`)
- `{version}` - the Bible version (ex: `KJV`)
- `{text}` - the verse text

Placeholders are replaced before MiniMessage parsing.

## Common Tags
Colors:
- `<black>`, `<dark_blue>`, `<dark_green>`, `<dark_aqua>`, `<dark_red>`, `<dark_purple>`
- `<gold>`, `<gray>`, `<dark_gray>`, `<blue>`, `<green>`, `<aqua>`, `<red>`, `<light_purple>`, `<yellow>`, `<white>`

Formatting:
- `<bold>...</bold>`
- `<italic>...</italic>`
- `<underlined>...</underlined>`
- `<strikethrough>...</strikethrough>`
- `<obfuscated>...</obfuscated>`
- `<reset>`

## Examples
Default style:
```
<gold>[VOTD] <yellow>{reference} ({version}) <white>{text}
```

Bold header:
```
<gold><bold>[VOTD]</bold></gold> <yellow>{reference} ({version}) <white>{text}
```

Two-tone verse:
```
<gold>[VOTD]</gold> <gray>{reference} ({version})</gray> <white>{text}
```

## Legacy Fallback
If you prefer legacy formatting, you can use `&` codes:
```
&6[VOTD] &e{reference} ({version}) &f{text}
```
When a template has only `&` codes and no `<tag>` syntax, CraftedGateway uses legacy parsing.

## Notes
- Use `<reset>` if you want to clear formatting mid-line.
- Avoid unbalanced tags (ex: `<gold>` with no closing tag if you add nested formatting).
