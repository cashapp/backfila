import * as React from "react"
import { connect } from "react-redux"
import Axios from "axios"
import {
  IDispatchProps,
  IState,
  mapDispatchToProps,
  mapStateToProps
} from "../ducks"
import {
  H1,
  FormGroup,
  NumericInput,
  InputGroup,
  Intent,
  Checkbox,
  Button,
  H2,
  H5,
  Tooltip,
  Classes,
  Spinner
} from "@blueprintjs/core"
import { simpleSelectorGet } from "@misk/simpleredux"
import { BackfillSelector } from "../components"
import { FlexContainer } from "@misk/core"
import { Link } from "react-router-dom"
import { FormEvent } from "react"
import { IBackfill } from "../components"
import { LayoutContainer } from "../containers"

interface CreateFormState {
  loading: boolean
  errorText?: string
  backfill?: IBackfill
  dry_run: boolean

  scan_size: number
  batch_size: number
  num_threads: number
  pkey_range_start?: string
  pkey_range_end?: string
  extra_sleep_ms: number
  backoff_schedule?: string
  parameters: any
}

class CreateFormContainer extends React.Component<
  IState & IDispatchProps,
  IState & CreateFormState
> {
  private service: string = (this.props as any).match.params.service
  private registeredBackfills: string = `${this.service}::BackfillRuns`

  componentDidMount() {
    this.props.simpleNetworkGet(
      this.registeredBackfills,
      `/services/${this.service}/registered-backfills`
    )
    this.setState({
      loading: false,
      errorText: null,
      backfill: null,
      dry_run: true,
      scan_size: 10000,
      batch_size: 100,
      num_threads: 1,
      pkey_range_start: null,
      pkey_range_end: null,
      backoff_schedule: null,
      extra_sleep_ms: 0,
      parameters: {}
    })
  }

  render() {
    let registeredBackfills = simpleSelectorGet(this.props.simpleNetwork, [
      this.registeredBackfills,
      "data"
    ])

    if (!registeredBackfills || !this.state) {
      return (
        <LayoutContainer>
          <H1>Service: {this.service}</H1>
          <Spinner />
        </LayoutContainer>
      )
    }
    return (
      <LayoutContainer>
        <H1>
          Service:{" "}
          <Link to={`/app/services/${this.service}`}>{this.service}</Link>
        </H1>
        <div style={{ width: "1000px", margin: "auto" }}>
          <H2>Create backfill</H2>

          <FormGroup>
            <FlexContainer>
              <H5>Name</H5>
              <BackfillSelector
                backfills={registeredBackfills.backfills}
                onValueChange={backfill =>
                  this.setState({
                    backfill: backfill,
                    parameters: {}
                  })
                }
              />
            </FlexContainer>
            <div hidden={!this.state.backfill}>
              Immutable options:
              <Checkbox
                checked={this.state.dry_run}
                label={"Dry Run"}
                onChange={() => this.setState({ dry_run: !this.state.dry_run })}
              />
              <FlexContainer>
                <H5>Range (optional)</H5>
                <InputGroup
                  id="text-input"
                  placeholder="Start"
                  onChange={(event: FormEvent<HTMLElement>) => {
                    this.setState({
                      pkey_range_start: (event.target as any).value
                    })
                  }}
                />
                <InputGroup
                  id="text-input"
                  placeholder="End"
                  onChange={(event: FormEvent<HTMLElement>) => {
                    this.setState({
                      pkey_range_end: (event.target as any).value
                    })
                  }}
                />
              </FlexContainer>
              Mutable options:
              <FlexContainer>
                <H5>
                  <Tooltip
                    className={Classes.TOOLTIP_INDICATOR}
                    content="How many *matching* records to send per call to RunBatch."
                  >
                    Batch Size
                  </Tooltip>
                </H5>
                <NumericInput
                  allowNumericCharactersOnly={true}
                  min={1}
                  minorStepSize={1}
                  onValueChange={(valueAsNumber: number) =>
                    this.setState({ batch_size: Math.max(valueAsNumber, 1) })
                  }
                  value={this.state.batch_size}
                />
              </FlexContainer>
              <FlexContainer>
                <H5>
                  <Tooltip
                    className={Classes.TOOLTIP_INDICATOR}
                    content="How many records to scan when computing batches."
                  >
                    Scan Size
                  </Tooltip>
                </H5>
                <NumericInput
                  allowNumericCharactersOnly={true}
                  min={1}
                  minorStepSize={1}
                  onValueChange={(valueAsNumber: number) =>
                    this.setState({ scan_size: Math.max(valueAsNumber, 1) })
                  }
                  value={this.state.scan_size}
                />
              </FlexContainer>
              <FlexContainer>
                <H5>Threads per partition</H5>
                <NumericInput
                  allowNumericCharactersOnly={true}
                  min={1}
                  minorStepSize={1}
                  onValueChange={(valueAsNumber: number) =>
                    this.setState({ num_threads: Math.max(valueAsNumber, 1) })
                  }
                  value={this.state.num_threads}
                />
              </FlexContainer>
              <FlexContainer>
                <H5>Extra sleep (ms)</H5>
                <NumericInput
                  allowNumericCharactersOnly={true}
                  min={0}
                  minorStepSize={1}
                  onValueChange={(valueAsNumber: number) =>
                    this.setState({ extra_sleep_ms: valueAsNumber })
                  }
                  value={this.state.extra_sleep_ms}
                />
              </FlexContainer>
              <FlexContainer>
                <H5>
                  <Tooltip
                    className={Classes.TOOLTIP_INDICATOR}
                    content="Comma separated list of milliseconds to backoff on subsequent failures"
                  >
                    Backoff Schedule (optional)
                  </Tooltip>
                </H5>
                <InputGroup
                  id="text-input"
                  placeholder="5000,15000,30000"
                  onChange={(event: FormEvent<HTMLElement>) => {
                    this.setState({
                      backoff_schedule: (event.target as any).value
                    })
                  }}
                />
              </FlexContainer>
              {this.state.backfill && this.state.backfill.parameterNames && (
                <div>
                  Immutable custom parameters:
                  {this.state.backfill.parameterNames.map((name: string) => (
                    <FlexContainer>
                      <H5>{name}</H5>
                      <InputGroup
                        id="text-input"
                        onChange={(event: FormEvent<HTMLElement>) => {
                          let newParams = Object.assign(
                            {},
                            this.state.parameters
                          )
                          let value = (event.target as any).value
                          // The server wants base64
                          newParams[name] = this.base64(value)
                          this.setState({ parameters: newParams })
                        }}
                      />
                    </FlexContainer>
                  ))}
                </div>
              )}
              <Button
                onClick={() => {
                  Axios.post(`/services/${this.service}/create`, {
                    backfill_name: this.state.backfill.name,
                    dry_run: this.state.dry_run,
                    scan_size: this.state.scan_size,
                    batch_size: this.state.batch_size,
                    num_threads: this.state.num_threads,
                    pkey_range_start: this.nullIfEmpty(
                      this.base64(this.state.pkey_range_start)
                    ),
                    pkey_range_end: this.nullIfEmpty(
                      this.base64(this.state.pkey_range_end)
                    ),
                    backoff_schedule: this.nullIfEmpty(
                      this.state.backoff_schedule
                    ),
                    extra_sleep_ms: this.state.extra_sleep_ms,
                    parameter_map: this.state.parameters
                  })
                    .then(response => {
                      let id = response.data.backfill_run_id
                      let history = (this.props as any).history
                      history.push(`/app/backfills/${id}`)
                    })
                    .catch(error => {
                      console.log(error)
                      this.setState({
                        loading: false,
                        errorText: error.response.data
                      })
                    })
                }}
                intent={Intent.PRIMARY}
                loading={this.state.loading}
                disabled={!this.state.backfill}
                text={"Create"}
              />
            </div>
            {this.state.errorText && (
              <div style={{ color: "red" }}>{this.state.errorText}</div>
            )}
          </FormGroup>
        </div>
      </LayoutContainer>
    )
  }

  private nullIfEmpty(str: string) {
    return str !== undefined && str !== null && str.length === 0 ? null : str
  }

  private base64(str: string) {
    return str ? btoa(str) : str
  }
}

export default connect(mapStateToProps, mapDispatchToProps)(CreateFormContainer)
