import * as React from "react"
import {Intent, ProgressBar} from "@blueprintjs/core"

export interface IBackfillProgressBarProps {
  precomputing_done: boolean
  backfilled_matching_record_count: number
  computed_matching_record_count: number
  state: string
}

export function BackfillProgressBar(props: IBackfillProgressBarProps) {
  const precomputing_done = props.precomputing_done
  const progress = props.backfilled_matching_record_count
  const max = props.computed_matching_record_count
  const value = precomputing_done && max > 0 ? progress / max : null
  const animate = props.state == "RUNNING"

  let intent: Intent
  if (props.state == "COMPLETE") {
    intent = Intent.SUCCESS
  } else if (!precomputing_done) {
    intent = Intent.NONE
  } else {
    intent = Intent.PRIMARY
  }

  return <ProgressBar value={value} intent={intent} animate={animate} />
}

export default BackfillProgressBar
