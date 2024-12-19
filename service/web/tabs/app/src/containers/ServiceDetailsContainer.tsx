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
  BackfillSelector,
  IBackfill
} from "../components"
import { simpleSelectorGet } from "@misk/simpleredux"
import { Link } from "react-router-dom"
import { LayoutContainer } from "."
import { RESERVED_VARIANT } from "../utilities"

interface BackfillSearchState {
  loading: boolean
  errorText?: string

  backfill?: IBackfill
  author?: string
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
      backfill: null,
      author: null, 
    })
    this.fetchBackfillRuns()
  }

  fetchBackfillRuns(backfill?: IBackfill, author?: string) {
    var url = `/services/${this.service}/variants/${this.variant}/backfill-runs`

    if (backfill && author) {
      url += `?backfill_name=${backfill.name}&created_by_user=${author}`
    }
    else {
      if (backfill) {
        url += `?backfill_name=${backfill.name}`
      }
      if (author) {
        url += `?created_by_user=${author}`
      }
    }

    this.props.simpleNetworkGet(this.backfillRunsTag, url)

    this.setState({
      loading: false,
      errorText: null,
    })
  }

  handleKeyPress = (event: React.KeyboardEvent<HTMLElement>) => {
    if (event.key === "Enter") {
      this.setState({ loading: true })
      this.fetchBackfillRuns(this.state.backfill, this.state.author)
    }
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
    if (this.variant != RESERVED_VARIANT) {
      return (
        <LayoutContainer>
          <ServiceHeader serviceName={this.service} variant={this.variant} />
          <Link
            to={`/app/services/${this.service}/variants/${this.variant}/create`}
          >
            <AnchorButton text={"Create"} intent={Intent.PRIMARY} />
          </Link>
          <br />
          <br />
          <H3>Running Backfills</H3>
          <BackfillRunsTable backfillRuns={result.running_backfills} />
          <H3>Paused Backfills</H3>
          <BackfillRunsTable backfillRuns={result.paused_backfills} />
          {result.next_pagination_token && (
            <div style={{ paddingBottom: "100px" }}>
              <Link
                to={`/app/services/${this.service}/variants/${this.variant}/runs/${result.next_pagination_token}`}
              >
                more
              </Link>
            </div>
          )}
        </LayoutContainer>
      )
    }
    return (
      <LayoutContainer>
        <ServiceHeader serviceName={this.service} variant={this.variant} />
        <Link to={`/app/services/${this.service}/create`}>
          <AnchorButton text={"Create"} intent={Intent.PRIMARY} />
        </Link>
        <br />
        <br />
        <FormGroup>
          <div style={{ display: "flex", gap: "10px" }}>
            <div style={{ flex: 1 }}>
              <H5>Backfill Name</H5>
              <BackfillSelector
                backfills={registeredBackfills.backfills}
                onValueChange={ newBackfill =>
                  this.setState({ backfill: newBackfill })
                }
                selected_item={this.state.backfill}
              />
            </div>
            <div style={{ flex: 1 }}>
              <H5>Author</H5>
              <InputGroup
                id="text-input"
                fill={false}
                value={this.state.author}
                placeholder="kara.dietz"
                onChange={(event: React.FormEvent<HTMLElement>) => {
                  this.setState({
                    author: (event.target as any).value
                  })
                }}
                onKeyPress={this.handleKeyPress}
              />
            </div>
          </div>
          <Button
            onClick={() => {
              this.setState({ loading: true })
              this.fetchBackfillRuns(this.state.backfill, this.state.author)
            }}
            intent={Intent.PRIMARY}
            loading={this.state.loading}
            disabled={!this.state.backfill && !this.state.author}
            text={"Filter"}
          />
        </FormGroup>
        <br />
        <H3>Running Backfills</H3>
        <BackfillRunsTable backfillRuns={result.running_backfills} />
        <H3>Paused Backfills</H3>
        <BackfillRunsTable backfillRuns={result.paused_backfills} />
        {result.next_pagination_token && (
          <div style={{ paddingBottom: "100px" }}>
            <Link
              to={`/app/services/${this.service}/runs/${result.next_pagination_token}`}
            >
              more
            </Link>
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
