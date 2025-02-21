import * as React from "react"
import { connect } from "react-redux"
import {
  IDispatchProps,
  IState,
  mapDispatchToProps,
  mapStateToProps
} from "../ducks"
import {
  H3,
  H5,
  FormGroup,
  InputGroup,
  Button,
  Intent,
  AnchorButton,
  Spinner
} from "@blueprintjs/core"
import {
  BackfillRunsTable,
  ServiceHeader,
  BackfillSelector
} from "../components"
import { simpleSelectorGet } from "@misk/simpleredux"
import { Link } from "react-router-dom"
import { LayoutContainer } from "."
import { RESERVED_VARIANT } from "../utilities"

interface BackfillSearchState {
  loading: boolean
  errorText?: string

  backfillName?: string
  createdBy?: string
}

class ServiceDetailsContainer extends React.Component<
  IState & IDispatchProps,
  IState & BackfillSearchState
> {
  private service: string = (this.props as any).match.params.service
  private variant: string =
    (this.props as any).match.params.variant ?? RESERVED_VARIANT
  private backfillRunsTag: string = `${this.service}::${this.variant}::BackfillRuns`
  private registeredBackfills: string = `${this.service}::BackfillRuns`

  componentDidMount() {
    this.props.simpleNetworkGet(
      this.registeredBackfills,
      `/services/${this.service}/variants/${this.variant}/registered-backfills`
    )

    this.setState({
      loading: false,
      errorText: null,
      backfillName: null,
      createdBy: null
    })

    this.fetchBackfillRuns()
  }

  fetchBackfillRuns(
    backfillName?: string,
    createdBy?: string,
    next_pagination_token?: string
  ) {
    const url = new URL(
      `/services/${this.service}/variants/${this.variant}/backfill-runs`,
      window.location.origin
    )
    const params = new URLSearchParams()

    if (next_pagination_token) {
      params.append("pagination_token", next_pagination_token)
    }
    if (backfillName) {
      params.append("backfill_name", backfillName)
    }
    if (createdBy) {
      params.append("created_by_user", createdBy)
    }
    url.search = params.toString()
    this.props.simpleNetworkGet(this.backfillRunsTag, url.toString())

    this.setState({
      loading: false,
      errorText: null
    })
  }

  fetchNextPage(pagination_token: string) {
    this.fetchBackfillRuns(
      this.state.backfillName,
      this.state.createdBy,
      pagination_token
    )
  }

  filterBackfills = () => {
    this.setState({ loading: true })
    this.fetchBackfillRuns(this.state.backfillName, this.state.createdBy)
  }

  handleKeyPress = (event: React.KeyboardEvent<HTMLElement>) => {
    if (event.key === "Enter") {
      this.filterBackfills()
    }
  }

  handleClearFilters = () => {
    this.setState({
      backfillName: null,
      createdBy: ""
    })
    this.fetchBackfillRuns()
  }

  render() {
    let result = simpleSelectorGet(this.props.simpleNetwork, [
      this.backfillRunsTag,
      "data"
    ])

    let registeredBackfills = simpleSelectorGet(this.props.simpleNetwork, [
      this.registeredBackfills,
      "data"
    ])
    if (!this.service || !result) {
      return (
        <LayoutContainer>
          <ServiceHeader serviceName={this.service} variant={this.variant} />
          <Spinner />
        </LayoutContainer>
      )
    }

    return (
      <LayoutContainer>
        {this.variant != RESERVED_VARIANT ? (
          <div>
            <ServiceHeader serviceName={this.service} variant={this.variant} />
            <Link
              to={`/app/services/${this.service}/variants/${this.variant}/create`}
            >
              <AnchorButton text={"Create"} intent={Intent.PRIMARY} />
            </Link>
          </div>
        ) : (
          <div>
            <ServiceHeader serviceName={this.service} variant={this.variant} />
            <Link to={`/app/services/${this.service}/create`}>
              <AnchorButton text={"Create"} intent={Intent.PRIMARY} />
            </Link>
          </div>
        )}
        <br />
        <br />
        <FormGroup>
          <div style={{ display: "flex", gap: "10px", alignItems: "center" }}>
            <div style={{ flex: 1 }}>
              <H5>Backfill Name</H5>
              <BackfillSelector
                backfills={registeredBackfills.backfills}
                onValueChange={selectedBackfill =>
                  this.setState({ backfillName: selectedBackfill.name })
                }
                selected_item={{
                  name: this.state.backfillName ?? "",
                  parameterNames: []
                }}
                onQueryChange={query => this.setState({ backfillName: query })}
              />
            </div>
            <div style={{ flex: 1 }}>
              <H5>Created by</H5>
              <InputGroup
                id="text-input"
                fill={false}
                value={this.state.createdBy}
                placeholder="first.last"
                onChange={(event: React.FormEvent<HTMLElement>) => {
                  this.setState({
                    createdBy: (event.target as any).value
                  })
                }}
                onKeyPress={this.handleKeyPress}
              />
            </div>
            <div style={{ flex: 1 }}>
              <Button
                text={"Filter"}
                onClick={() => {
                  this.filterBackfills()
                }}
                intent={Intent.NONE}
                minimal={true}
                loading={this.state.loading}
                disabled={!this.state.backfillName && !this.state.createdBy}
              />
              <Button
                text={"Clear filters"}
                onClick={this.handleClearFilters}
                intent={Intent.PRIMARY}
                minimal={true}
                disabled={!this.state.backfillName && !this.state.createdBy}
              />
            </div>
          </div>
        </FormGroup>
        <H3>Running Backfills</H3>
        <BackfillRunsTable backfillRuns={result.running_backfills} />
        <H3>Paused Backfills</H3>
        <BackfillRunsTable backfillRuns={result.paused_backfills} />
        {result.next_pagination_token && (
          <div style={{ paddingBottom: "100px" }}>
            <Button
              text={"Next"}
              onClick={() => {
                this.fetchNextPage(result.next_pagination_token)
              }}
              intent={Intent.PRIMARY}
              minimal={true}
            />
          </div>
        )}
      </LayoutContainer>
    )
  }
}

export default connect(
  mapStateToProps,
  mapDispatchToProps
)(ServiceDetailsContainer)
