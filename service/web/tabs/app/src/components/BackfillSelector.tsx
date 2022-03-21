/** @jsx jsx */

import * as React from "react"

import { MenuItem } from "@blueprintjs/core"
import { Global, css, jsx } from "@emotion/core"
import { ItemPredicate, ItemRenderer, Suggest } from "@blueprintjs/select"

export interface IBackfill {
  name: string
  parameterNames: string[]
}

export interface IBackfillSelectorProps {
  backfills: IBackfill[]

  onValueChange?(value: IBackfill): void
}

export default class BackfillSelector extends React.PureComponent<
  IBackfillSelectorProps
> {
  public render() {
    return (
      <div style={{ width: "100%" }}>
        <Global
          styles={css`
            .bp3-select-popover .bp3-menu {
              max-width: 100% !important;
            }
          `}
        />
        <Suggest<IBackfill>
          fill={true}
          popoverProps={{ minimal: true }}
          inputValueRenderer={this.renderInputValue}
          items={this.props.backfills}
          noResults={<MenuItem disabled={true} text="No backfills." />}
          onItemSelect={this.props.onValueChange}
          itemPredicate={this.filterBackfill}
          itemRenderer={this.renderBackfill}
        />
      </div>
    )
  }

  private renderInputValue = (item: IBackfill) => item.name

  private renderBackfill: ItemRenderer<IBackfill> = (
    item,
    { handleClick, modifiers, query }
  ) => {
    if (!modifiers.matchesPredicate) {
      return null
    }
    const text = item.name
    return (
      <MenuItem
        active={modifiers.active}
        disabled={modifiers.disabled}
        key={item.name}
        onClick={handleClick}
        text={BackfillSelector.highlightText(text, query)}
      />
    )
  }

  private filterBackfill: ItemPredicate<IBackfill> = (
    query,
    backfill,
    _index,
    exactMatch
  ) => {
    const normalizedTitle = backfill.name.toLowerCase()
    const normalizedQuery = query.toLowerCase()

    if (exactMatch) {
      return normalizedTitle === normalizedQuery
    } else {
      return normalizedTitle.indexOf(normalizedQuery) >= 0
    }
  }

  static highlightText(text: string, query: string) {
    let lastIndex = 0
    const words = query
      .split(/\s+/)
      .filter(word => word.length > 0)
      .map(BackfillSelector.escapeRegExpChars)
    if (words.length === 0) {
      return [text]
    }
    const regexp = new RegExp(words.join("|"), "gi")
    const tokens: React.ReactNode[] = []
    while (true) {
      const match = regexp.exec(text)
      if (!match) {
        break
      }
      const length = match[0].length
      const before = text.slice(lastIndex, regexp.lastIndex - length)
      if (before.length > 0) {
        tokens.push(before)
      }
      lastIndex = regexp.lastIndex
      tokens.push(<strong key={lastIndex}>{match[0]}</strong>)
    }
    const rest = text.slice(lastIndex)
    if (rest.length > 0) {
      tokens.push(rest)
    }
    return tokens
  }

  static escapeRegExpChars(text: string) {
    return text.replace(/([.*+?^=!:${}()|\[\]\/\\])/g, "\\$1")
  }
}
