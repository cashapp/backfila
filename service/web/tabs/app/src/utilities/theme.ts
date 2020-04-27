import {
  defaultTheme,
  color,
  environmentToColor,
  IEnvironmentToColorLookup,
  ITheme
} from "@misk/core"

const environmentColorLookup: IEnvironmentToColorLookup = {
  default: color.cadet,
  DEVELOPMENT: color.platinum,
  TESTING: color.purple,
  STAGING: color.yellow,
  PRODUCTION: color.red
}

export const backfilaTheme: ITheme = {
  ...defaultTheme,
  environmentToColor: environmentToColor(environmentColorLookup)
}
