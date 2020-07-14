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
  Spinner,
  Radio,
  RadioGroup
} from "@blueprintjs/core"
import { FlexContainer } from "@misk/core"
import { Link } from "react-router-dom"
import { FormEvent } from "react"
import { IBackfill } from "../components"
import { LayoutContainer } from "../containers"

interface CloneFormState {
  loading: boolean
  errorText?: string

  statusResponse: any

  backfills: IBackfill[]
  backfill?: IBackfill
  dry_run: boolean

  scan_size: number
  batch_size: number
  num_threads: number
  range_clone_type: string
  pkey_range_start?: string
  pkey_range_end?: string
  extra_sleep_ms: number
  backoff_schedule?: string
  parameters: any
}

class CloneFormContainer extends React.Component<
  IState & IDispatchProps,
  IState & CloneFormState
> {
  private id: string = (this.props as any).match.params.id

  componentDidMount() {
    this.setState({
      loading: false,
      errorText: null,

      statusResponse: null,

      backfill: null,

      dry_run: false,
      scan_size: 10000,
      batch_size: 100,
      num_threads: 1,
      range_clone_type: "RESTART",
      pkey_range_start: null,
      pkey_range_end: null,
      backoff_schedule: null,
      extra_sleep_ms: 0,
      parameters: {}
    })
    Axios.get(`/backfills/${this.id}/status`)
      .then(response => {
        let params: any = {}
        Object.keys(response.data.parameters).map(function(key, index) {
          let value = response.data.parameters[key]
          params[key] = new Buffer(value).toString("base64")
        })
        this.setState({
          statusResponse: response.data,

          // Initialize these values from the existing backfill data.
          dry_run: response.data.dry_run,
          scan_size: response.data.scan_size,
          batch_size: response.data.batch_size,
          num_threads: response.data.num_threads,
          backoff_schedule: response.data.backoff_schedule,
          extra_sleep_ms: response.data.extra_sleep_ms,
          parameters: params
        })
        this.requestRegisteredBackfills(
          response.data.service_name,
          response.data.name
        )
      })
      .catch(error => {
        console.log(error)
      })
  }

  requestRegisteredBackfills(service: string, backfillName: string) {
    Axios.get(`/services/${service}/registered-backfills`)
      .then(response => {
        let selected = response.data.backfills.find(
          (b: IBackfill) => b.name == backfillName
        )
        if (selected) {
          this.setState({
            backfills: response.data.backfills,
            backfill: selected
          })
        } else {
          this.setState({ errorText: "Backfill doesn't exist" })
        }
      })
      .catch(error => {
        console.log(error)
      })
  }

  render() {
    if (!this.state || !this.state.backfills) {
      return (
        <LayoutContainer>
          <H2>Clone backfill # {this.id}</H2>
          <Spinner />
        </LayoutContainer>
      )
    }

    return (
      <LayoutContainer>
        <H1>
          Service:{" "}
          <Link to={`/app/services/${this.state.statusResponse.service_name}`}>
            {this.state.statusResponse.service_name}
          </Link>
        </H1>
        <div style={{ width: "1000px", margin: "auto" }}>
          <H2>
            Clone backfill{" "}
            <Link to={`/app/backfills/${this.id}`}>#{this.id}</Link>{" "}
            {this.state.statusResponse.name}
          </H2>

          <FormGroup>
            <div hidden={!this.state.backfill}>
              Immutable options:
              <Checkbox
                checked={this.state.dry_run}
                label={"Dry Run"}
                onChange={() => this.setState({ dry_run: !this.state.dry_run })}
              />
              <RadioGroup
                label="Range treatment"
                onChange={(event: FormEvent<HTMLElement>) =>
                  this.setState({
                    range_clone_type: (event.target as any).value
                  })
                }
                selectedValue={this.state.range_clone_type}
              >
                <Radio
                  label="Same range, restart from beginning"
                  value="RESTART"
                />
                <Radio
                  label="Same range, continue from last processed"
                  value="CONTINUE"
                />
                <Radio label="New range" value="NEW" />
              </RadioGroup>
              {this.state.range_clone_type == "NEW" && (
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
              )}
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
                  value={this.state.backoff_schedule}
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
                        value={
                          this.state.parameters[name]
                            ? atob(this.state.parameters[name])
                            : ""
                        }
                        onChange={(event: FormEvent<HTMLElement>) => {
                          let newParams = Object.assign(
                            {},
                            this.state.parameters
                          )
                          let value = (event.target as any).value
                          // The server wants base64
                          newParams[name] = new Buffer(value).toString("base64")
                          this.setState({ parameters: newParams })
                        }}
                      />
                    </FlexContainer>
                  ))}
                </div>
              )}
              <Button
                onClick={() => {
                  Axios.post(`/backfills/${this.id}/clone`, {
                    dry_run: this.state.dry_run,
                    scan_size: this.state.scan_size,
                    batch_size: this.state.batch_size,
                    num_threads: this.state.num_threads,
                    range_clone_type: this.state.range_clone_type,
                    pkey_range_start: this.nullIfEmpty(
                      this.state.pkey_range_start
                    ),
                    pkey_range_end: this.nullIfEmpty(this.state.pkey_range_end),
                    backoff_schedule: this.nullIfEmpty(
                      this.state.backoff_schedule
                    ),
                    extra_sleep_ms: this.state.extra_sleep_ms,
                    parameter_map: this.state.parameters
                  })
                    .then(response => {
                      let id = response.data.id
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
                text={"Clone"}
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
}

export default connect(mapStateToProps, mapDispatchToProps)(CloneFormContainer)
