import * as React from "react"
import Axios from "axios"
import {
  NumericInput,
  AnchorButton,
  InputGroup,
  Toaster,
  Intent
} from "@blueprintjs/core"
import { FlexContainer } from "@misk/core"
import { FormEvent } from "react"

export interface IEditableFieldProps {
  id: string
  fieldName: string
  numeric: boolean
  value: any
  placeholder?: string
  minValue?: number
  onUpdate?: () => void
}

interface IEditableFieldState {
  loading: boolean
  editing: boolean
  editingValue: any
}

export default class StartStopButton extends React.Component<
  IEditableFieldProps,
  IEditableFieldState
> {
  private id: string = this.props.id

  public state: IEditableFieldState = {
    loading: false,
    editing: false,
    editingValue: null
  }

  update() {
    const url = `/backfills/${this.id}/update`
    this.setState({ loading: true })
    let postData: any = {}
    postData[this.props.fieldName] = this.state.editingValue
    Axios.post(url, postData)
      .then(response => {
        this.setState({
          loading: false,
          editing: false
        })
        if (this.props.onUpdate) {
          this.props.onUpdate()
        }
      })
      .catch(error => {
        this.setState({
          loading: false
        })
        Toaster.create().show({
          intent: Intent.DANGER,
          message: `Error: ${error.response.data}`
        })
      })
  }

  render() {
    if (this.state.editing) {
      return (
        <FlexContainer>
          {this.props.numeric && (
            <NumericInput
              allowNumericCharactersOnly={true}
              min={this.props.minValue}
              minorStepSize={1}
              onValueChange={(valueAsNumber: number) =>
                this.setState({
                  editingValue: Math.max(valueAsNumber, this.props.minValue)
                })
              }
              value={this.state.editingValue}
            />
          )}
          {!this.props.numeric && (
            <InputGroup
              id="text-input"
              placeholder={this.props.placeholder}
              onChange={(event: FormEvent<HTMLElement>) => {
                this.setState({
                  editingValue: (event.target as any).value
                })
              }}
              defaultValue={this.state.editingValue}
            />
          )}
          <AnchorButton
            icon={"tick"}
            style={{ float: "right" }}
            small={true}
            minimal={true}
            loading={this.state.loading}
            onClick={() => {
              this.update()
            }}
          />
        </FlexContainer>
      )
    } else {
      return (
        <span>
          {this.props.value}
          <AnchorButton
            icon={"edit"}
            style={{ float: "right" }}
            small={true}
            minimal={true}
            onClick={() =>
              this.setState({
                editing: true,
                editingValue: this.props.value
              })
            }
          />
        </span>
      )
    }
  }
}
