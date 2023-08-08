import { simpleSelectorGet } from "@misk/simpleredux"
import * as React from "react"
import { connect } from "react-redux"
import {
    IDispatchProps,
    IState,
    mapDispatchToProps,
    mapStateToProps
} from "src/ducks"
import { LayoutContainer } from "."
import FlavorsListComponent from "../components/FlavorsListComponent";

class ServiceFlavorsContainer extends React.Component<IState & IDispatchProps, IState> {
    private service: string = (this.props as any).match.params.service
    private serviceFlavorsTag: string = `${this.service}::Flavors`

    componentDidMount() {
        this.sendRequest()
    }

    sendRequest() {
      this.props.simpleNetworkGet(
        this.serviceFlavorsTag,
        `/services/${this.service}/flavors`
      )
    }

    render() {
        return (
            <LayoutContainer>
                <FlavorsListComponent
                    data={simpleSelectorGet(
                        this.props.simpleNetwork,
                        [this.serviceFlavorsTag, "data", "flavors"],
                        []
                    )}
                    service={this.service}
                />
            </LayoutContainer>
        )
    }
}

export default connect(mapStateToProps, mapDispatchToProps)(ServiceFlavorsContainer)
