---
title: Writing articles
summary: Markdown files in a resource pack or mod. Front matter, links, images, item bindings.
icon: minecraft:writable_book
keywords: [wiki, markdown, resource pack, front matter, modding]
---
# Writing articles

An article is a file `assets/<namespace>/wiki/<path>.md`. Its id is `<namespace>:<path>`. Resource packs add, replace and extend articles; `F3+T` reloads them.

## Front matter

```
---
title: Shown in the list
summary: One line under the title
icon: minecraft:book        # item id, or a .png texture id
items: [mymod:gizmo, "#c:ingots"]
keywords: [synonyms, for, search]
priority: 10                # higher first
hidden: false               # true = reachable by links and bindings only
---
```

All keys optional. Missing `title` = first heading; missing `summary` = first paragraph; missing `icon` = first bound item.

## Text

**bold**, *italic*, ~~strike~~, `code` and [links](../pda.md). Line break with two spaces or `<br>`.

> Block quotes are dimmed and ruled.

1. Ordered lists
2. Numbered from the first number
   - nested bullets
   - work too

- [x] Task lists
- [ ] with check boxes

---

## Tables

| key | meaning |
|:---|---:|
| left | right |
| `code` | **bold** |

## Links

| form | target |
|---|---|
| `[x](pda.md)` | article in the same folder, `.md` optional |
| `[x](../pda.md)` | parent folder |
| `[x](/pda.md)` | namespace root |
| `[x](othermod:path)` | another namespace |
| `[x](#links)` | heading in this article |
| `[x](pda.md#apps)` | heading in another |

Broken links are drawn red.

## Images

![item icon](item:minecraft:diamond) inline item icons with `item:<id>`.

`![alt](textures/foo.png)` is relative to this article's folder under `wiki/`; `![alt](/img/foo.png)` from `wiki/`; `![alt](ns:textures/gui/foo.png)` any texture. Wider than the page => scaled down.

```
code blocks are monospaced
and never wrap words
```
