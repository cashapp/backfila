import * as React from "react"
import { Route, Switch } from "react-router"
import {
  BackfillStatusContainer,
  CreateFormContainer,
  HomeContainer,
  LayoutContainer,
  ServiceContainer
} from "../containers"

const routes = (
  <div>
    <Switch>
      <Route
        path="/app/services/:service/create"
        component={CreateFormContainer}
      />
      <Route path="/app/services/:service" component={ServiceContainer} />
      <Route path="/app/backfills/:id" component={BackfillStatusContainer} />
      <Route path="/_admin/app/" component={HomeContainer} />
      <Route path="/app/" component={HomeContainer} />
      <Route path="/_tab/app/" component={HomeContainer} />
      <Route path="/app/*" component={LayoutContainer} />
      {/* Do not include a Route without a path or it will display during on all tabs */}
    </Switch>
  </div>
)

export default routes
