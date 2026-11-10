// What jest.text-transform.js turns these imports into: the file's text.
declare module '*.yaml' {
  const text: string;
  export default text;
}

declare module '*.yml' {
  const text: string;
  export default text;
}
